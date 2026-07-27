import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react'

import {
  ApiError,
  closePaperPosition,
  getPaperTradingPortfolio,
  openPaperPosition,
  SessionUserMismatchError,
  updatePaperPositionRiskControls,
  type OpenPaperPositionRequest,
  type ClosedPaperTrade,
  type OpenPaperPosition,
  type PaperTradingAccount,
  type PaperTradingPortfolio,
  type PaperTradingQuote,
  type PositionSide,
  type UpdateRiskControlsRequest,
} from './api.ts'
import { BtcChart } from './BtcChart.tsx'

const PORTFOLIO_POLL_INTERVAL_MS = 4_000

const usdFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const compactUsdFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 2,
})

const priceFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const quantityFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 8,
})

const percentFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
})

const timeFormatter = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

type TradingWorkspaceProps = {
  expectedUserId: string
  onSessionExpired: () => void
}

type SnapshotMutation = (
  signal: AbortSignal,
) => Promise<PaperTradingPortfolio>

function requestErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }

  return 'The paper-trading request failed.'
}

function pnlClass(value: number): string {
  if (value > 0) {
    return 'is-positive'
  }

  if (value < 0) {
    return 'is-negative'
  }

  return ''
}

function formatOptionalPrice(value: number | null): string {
  return value === null ? 'Not set' : `$${priceFormatter.format(value)}`
}

function parseOptionalPrice(value: string): number | null | undefined {
  if (value.trim() === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function validateRiskControls(
  side: PositionSide,
  quote: PaperTradingQuote,
  stopLoss: number | null,
  takeProfit: number | null,
): string | null {
  if (side === 'LONG') {
    if (stopLoss !== null && stopLoss >= quote.bidPrice) {
      return 'A long stop loss must be below the current bid.'
    }

    if (takeProfit !== null && takeProfit <= quote.askPrice) {
      return 'A long take profit must be above the current ask.'
    }
  } else {
    if (stopLoss !== null && stopLoss <= quote.askPrice) {
      return 'A short stop loss must be above the current ask.'
    }

    if (takeProfit !== null && takeProfit >= quote.bidPrice) {
      return 'A short take profit must be below the current bid.'
    }
  }

  return null
}

function PnlValue({
  value,
  children,
}: {
  value: number
  children?: ReactNode
}) {
  return (
    <span className={`pnl-value ${pnlClass(value)}`}>
      {children ?? usdFormatter.format(value)}
    </span>
  )
}

function AccountSummary({
  account,
  quote,
  lastUpdatedAt,
}: {
  account: PaperTradingAccount
  quote: PaperTradingQuote
  lastUpdatedAt: Date
}) {
  const balanceChange = account.balance - account.initialBalance

  return (
    <section className="account-summary" aria-label="Paper account summary">
      <div className="account-summary-heading">
        <div>
          <span className="panel-label">Cross-margin paper account</span>
          <h1>{usdFormatter.format(account.equity)}</h1>
          <span className="equity-caption">Total equity</span>
        </div>
        <div className="quote-block">
          <span className="live-indicator"><i /> Live quote</span>
          <strong>{quote.symbol}</strong>
          <span>
            Bid ${priceFormatter.format(quote.bidPrice)}
            <i />
            Ask ${priceFormatter.format(quote.askPrice)}
          </span>
          <small>Updated {timeFormatter.format(lastUpdatedAt)}</small>
        </div>
      </div>

      <div className="account-metrics">
        <div>
          <span>Wallet balance</span>
          <strong>{usdFormatter.format(account.balance)}</strong>
          <PnlValue value={balanceChange}>
            {percentFormatter.format(
              (balanceChange / account.initialBalance) * 100,
            )}% all time
          </PnlValue>
        </div>
        <div>
          <span>Available margin</span>
          <strong>{usdFormatter.format(account.availableMargin)}</strong>
          <small>Shared across all positions</small>
        </div>
        <div>
          <span>Used margin</span>
          <strong>{usdFormatter.format(account.usedMargin)}</strong>
          <small>
            Cross margin ·{' '}
            {account.equity === 0
              ? '—'
              : `${((account.usedMargin / account.equity) * 100).toFixed(2)}% of equity`}
          </small>
        </div>
        <div>
          <span>Unrealized PnL</span>
          <PnlValue value={account.unrealizedPnl}>
            <strong>{usdFormatter.format(account.unrealizedPnl)}</strong>
          </PnlValue>
          <small>Marked by the server</small>
        </div>
      </div>
    </section>
  )
}

function OrderTicket({
  portfolio,
  onSubmit,
}: {
  portfolio: PaperTradingPortfolio
  onSubmit: (
    request: OpenPaperPositionRequest,
  ) => Promise<PaperTradingPortfolio>
}) {
  const [side, setSide] = useState<PositionSide>('LONG')
  const [leverage, setLeverage] = useState(10)
  const [margin, setMargin] = useState('')
  const [stopLoss, setStopLoss] = useState('')
  const [takeProfit, setTakeProfit] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const retryOrderRef = useRef<{
    fingerprint: string
    clientOrderId: string
  } | null>(null)

  const executionPrice =
    side === 'LONG'
      ? portfolio.quote.askPrice
      : portfolio.quote.bidPrice
  const parsedMargin = Number(margin)
  const validMargin =
    margin.trim() !== '' && Number.isFinite(parsedMargin) && parsedMargin > 0
  const notional = validMargin ? parsedMargin * leverage : 0
  const quantity = validMargin ? notional / executionPrice : 0

  async function submitOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!validMargin) {
      setError('Enter a margin amount greater than $0.')
      return
    }

    if (parsedMargin > portfolio.account.availableMargin) {
      setError('Margin cannot exceed your available cross margin.')
      return
    }

    const parsedStopLoss = parseOptionalPrice(stopLoss)
    const parsedTakeProfit = parseOptionalPrice(takeProfit)

    if (parsedStopLoss === undefined || parsedTakeProfit === undefined) {
      setError('Stop loss and take profit must be positive prices when set.')
      return
    }

    const riskError = validateRiskControls(
      side,
      portfolio.quote,
      parsedStopLoss,
      parsedTakeProfit,
    )

    if (riskError !== null) {
      setError(riskError)
      return
    }

    setPending(true)
    const orderIntent = {
      side,
      leverage,
      marginUsd: parsedMargin,
      stopLoss: parsedStopLoss,
      takeProfit: parsedTakeProfit,
    }
    const fingerprint = JSON.stringify(orderIntent)
    const clientOrderId =
      retryOrderRef.current?.fingerprint === fingerprint
        ? retryOrderRef.current.clientOrderId
        : crypto.randomUUID()
    retryOrderRef.current = { fingerprint, clientOrderId }

    try {
      await onSubmit({
        clientOrderId,
        ...orderIntent,
      })
      retryOrderRef.current = null
      setMargin('')
      setStopLoss('')
      setTakeProfit('')
    } catch (requestError) {
      setError(requestErrorMessage(requestError))
    } finally {
      setPending(false)
    }
  }

  return (
    <aside className="order-ticket">
      <div className="ticket-heading">
        <div>
          <span className="panel-label">Order ticket</span>
          <h2>Open position</h2>
        </div>
        <span className="market-chip">BTC</span>
      </div>

      <div className="fixed-order-settings" aria-label="Fixed order settings">
        <span><small>Order type</small><strong>Market</strong></span>
        <span><small>Margin mode</small><strong>Cross</strong></span>
      </div>

      <form onSubmit={submitOrder}>
        <div className="side-selector" role="group" aria-label="Position side">
          <button
            className={side === 'LONG' ? 'is-long' : ''}
            type="button"
            aria-pressed={side === 'LONG'}
            onClick={() => {
              setSide('LONG')
              setError(null)
            }}
          >
            Long
          </button>
          <button
            className={side === 'SHORT' ? 'is-short' : ''}
            type="button"
            aria-pressed={side === 'SHORT'}
            onClick={() => {
              setSide('SHORT')
              setError(null)
            }}
          >
            Short
          </button>
        </div>

        <label className="ticket-field">
          <span>
            Margin
            <button
              type="button"
              onClick={() => {
                const maxCents = Math.floor(
                  portfolio.account.availableMargin * 100,
                ) / 100
                setMargin(Math.max(0, maxCents).toFixed(2))
              }}
            >
              Max {usdFormatter.format(portfolio.account.availableMargin)}
            </button>
          </span>
          <div className="input-with-affix">
            <input
              type="number"
              min="0.01"
              step="0.01"
              inputMode="decimal"
              placeholder="0.00"
              value={margin}
              onChange={(event) => setMargin(event.target.value)}
              disabled={pending}
            />
            <strong>USD</strong>
          </div>
        </label>

        <div className="leverage-control">
          <div>
            <span>Leverage</span>
            <output>{leverage}×</output>
          </div>
          <input
            type="range"
            min="1"
            max="100"
            step="1"
            value={leverage}
            onChange={(event) => setLeverage(event.target.valueAsNumber)}
            disabled={pending}
            aria-label="Leverage from 1 to 100 times"
          />
          <div className="leverage-scale">
            <span>1×</span><span>25×</span><span>50×</span><span>75×</span><span>100×</span>
          </div>
        </div>

        <div className="risk-grid">
          <label className="ticket-field">
            <span>Stop loss <small>Optional</small></span>
            <div className="input-with-affix">
              <input
                type="number"
                min="0.01"
                step="0.01"
                inputMode="decimal"
                placeholder="Price"
                value={stopLoss}
                onChange={(event) => setStopLoss(event.target.value)}
                disabled={pending}
              />
              <strong>USD</strong>
            </div>
          </label>
          <label className="ticket-field">
            <span>Take profit <small>Optional</small></span>
            <div className="input-with-affix">
              <input
                type="number"
                min="0.01"
                step="0.01"
                inputMode="decimal"
                placeholder="Price"
                value={takeProfit}
                onChange={(event) => setTakeProfit(event.target.value)}
                disabled={pending}
              />
              <strong>USD</strong>
            </div>
          </label>
        </div>

        <div className="order-preview">
          <span><small>Market {side === 'LONG' ? 'ask' : 'bid'}</small><strong>${priceFormatter.format(executionPrice)}</strong></span>
          <span><small>Position value</small><strong>{compactUsdFormatter.format(notional)}</strong></span>
          <span><small>Estimated quantity</small><strong>{quantityFormatter.format(quantity)} BTC</strong></span>
        </div>

        {error !== null && (
          <p className="form-error" role="alert">{error}</p>
        )}

        <button
          className={`submit-order ${side === 'LONG' ? 'long-order' : 'short-order'}`}
          type="submit"
          disabled={pending}
        >
          {pending ? 'Opening market position…' : `${side === 'LONG' ? 'Buy / Long' : 'Sell / Short'} ${leverage}×`}
        </button>
        <p className="ticket-disclaimer">
          Simulated execution only. No funds or wallet transaction required.
        </p>
      </form>
    </aside>
  )
}

function RiskControlEditor({
  position,
  quote,
  pending,
  onCancel,
  onSave,
}: {
  position: OpenPaperPosition
  quote: PaperTradingQuote
  pending: boolean
  onCancel: () => void
  onSave: (controls: UpdateRiskControlsRequest) => Promise<void>
}) {
  const [stopLoss, setStopLoss] = useState(
    position.stopLoss === null ? '' : String(position.stopLoss),
  )
  const [takeProfit, setTakeProfit] = useState(
    position.takeProfit === null ? '' : String(position.takeProfit),
  )
  const [error, setError] = useState<string | null>(null)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const parsedStopLoss = parseOptionalPrice(stopLoss)
    const parsedTakeProfit = parseOptionalPrice(takeProfit)

    if (parsedStopLoss === undefined || parsedTakeProfit === undefined) {
      setError('Risk controls must be positive prices when set.')
      return
    }

    const riskError = validateRiskControls(
      position.side,
      quote,
      parsedStopLoss,
      parsedTakeProfit,
    )

    if (riskError !== null) {
      setError(riskError)
      return
    }

    try {
      await onSave({
        stopLoss: parsedStopLoss,
        takeProfit: parsedTakeProfit,
      })
    } catch (requestError) {
      setError(requestErrorMessage(requestError))
    }
  }

  return (
    <form className="risk-editor" onSubmit={save}>
      <label>
        <span>Stop loss</span>
        <input
          type="number"
          min="0.01"
          step="0.01"
          placeholder="Not set"
          value={stopLoss}
          onChange={(event) => setStopLoss(event.target.value)}
          disabled={pending}
        />
      </label>
      <label>
        <span>Take profit</span>
        <input
          type="number"
          min="0.01"
          step="0.01"
          placeholder="Not set"
          value={takeProfit}
          onChange={(event) => setTakeProfit(event.target.value)}
          disabled={pending}
        />
      </label>
      <div className="risk-editor-actions">
        <button type="button" onClick={onCancel} disabled={pending}>Cancel</button>
        <button type="submit" disabled={pending}>
          {pending ? 'Saving…' : 'Save controls'}
        </button>
      </div>
      {error !== null && <p className="form-error" role="alert">{error}</p>}
    </form>
  )
}

function OpenPositions({
  portfolio,
  onUpdateRisk,
  onClose,
}: {
  portfolio: PaperTradingPortfolio
  onUpdateRisk: (
    positionId: string,
    controls: UpdateRiskControlsRequest,
  ) => Promise<PaperTradingPortfolio>
  onClose: (positionId: string) => Promise<PaperTradingPortfolio>
}) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [closingId, setClosingId] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const closingPosition =
    portfolio.openPositions.find((position) => position.id === closingId) ??
    null

  async function updateRisk(
    position: OpenPaperPosition,
    controls: UpdateRiskControlsRequest,
  ) {
    const actionKey = `risk:${position.id}`
    setPendingAction(actionKey)
    setActionError(null)

    try {
      await onUpdateRisk(position.id, controls)
      setEditingId(null)
    } catch (error) {
      throw error
    } finally {
      setPendingAction(null)
    }
  }

  async function closePosition(position: OpenPaperPosition) {
    const actionKey = `close:${position.id}`
    setPendingAction(actionKey)
    setActionError(null)

    try {
      await onClose(position.id)
      setClosingId(null)
    } catch (error) {
      setActionError(requestErrorMessage(error))
    } finally {
      setPendingAction(null)
    }
  }

  return (
    <section className="trades-panel">
      <header className="section-heading">
        <div>
          <span className="panel-label">Active risk</span>
          <h2>Open positions</h2>
        </div>
        <span className="count-chip">{portfolio.openPositions.length} open</span>
      </header>

      {portfolio.openPositions.length === 0 ? (
        <div className="empty-trades">
          <span>↗</span>
          <strong>No open positions</strong>
          <p>Choose a side, margin, and leverage in the market ticket to begin.</p>
        </div>
      ) : (
        <div className="positions-list">
          {portfolio.openPositions.map((position) => (
              <article className="position-card" key={position.id}>
                <div className="position-primary">
                  <span className={`side-badge ${position.side === 'LONG' ? 'is-long' : 'is-short'}`}>
                    {position.side}
                  </span>
                  <div>
                    <strong>{position.symbol}</strong>
                    <span>Market · Cross · {position.leverage}×</span>
                  </div>
                </div>

                <dl className="position-metrics">
                  <div><dt>Size</dt><dd>{compactUsdFormatter.format(position.notionalUsd)}</dd></div>
                  <div><dt>Entry</dt><dd>${priceFormatter.format(position.entryPrice)}</dd></div>
                  <div><dt>Mark</dt><dd>${priceFormatter.format(position.markPrice)}</dd></div>
                  <div>
                    <dt>Unrealized PnL</dt>
                    <dd><PnlValue value={position.unrealizedPnl}>{usdFormatter.format(position.unrealizedPnl)} <small>{percentFormatter.format(position.unrealizedRoePercent)}%</small></PnlValue></dd>
                  </div>
                  <div><dt>Stop loss</dt><dd>{formatOptionalPrice(position.stopLoss)}</dd></div>
                  <div><dt>Take profit</dt><dd>{formatOptionalPrice(position.takeProfit)}</dd></div>
                </dl>

                <div className="position-actions">
                  <button
                    type="button"
                    onClick={() => {
                      setEditingId(editingId === position.id ? null : position.id)
                      setClosingId(null)
                      setActionError(null)
                    }}
                    disabled={pendingAction !== null}
                  >
                    Edit SL / TP
                  </button>
                  <button
                    className="close-position-button"
                    type="button"
                    onClick={() => {
                      setClosingId(position.id)
                      setEditingId(null)
                      setActionError(null)
                    }}
                    disabled={pendingAction !== null}
                  >
                    Preview close
                  </button>
                </div>

                {editingId === position.id && (
                  <RiskControlEditor
                    key={position.id}
                    position={position}
                    quote={portfolio.quote}
                    pending={pendingAction === `risk:${position.id}`}
                    onCancel={() => setEditingId(null)}
                    onSave={(controls) => updateRisk(position, controls)}
                  />
                )}
              </article>
          ))}
        </div>
      )}

      {closingPosition !== null && (
        <ClosePreview
          position={closingPosition}
          quote={portfolio.quote}
          pending={pendingAction === `close:${closingPosition.id}`}
          error={actionError}
          onCancel={() => {
            setClosingId(null)
            setActionError(null)
          }}
          onConfirm={() => closePosition(closingPosition)}
        />
      )}
    </section>
  )
}

function ClosePreview({
  position,
  quote,
  pending,
  error,
  onCancel,
  onConfirm,
}: {
  position: OpenPaperPosition
  quote: PaperTradingQuote
  pending: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}) {
  const closePrice =
    position.side === 'LONG' ? quote.bidPrice : quote.askPrice
  const estimatedPnl = position.unrealizedPnl

  return (
    <div className="close-preview-backdrop" role="presentation">
      <section
        className="close-preview"
        role="dialog"
        aria-modal="true"
        aria-labelledby="close-preview-title"
      >
        <header>
          <div>
            <span className="panel-label">Executable-close preview</span>
            <h3 id="close-preview-title">Close {position.symbol} {position.side.toLowerCase()}?</h3>
          </div>
          <button type="button" onClick={onCancel} disabled={pending} aria-label="Cancel close">×</button>
        </header>

        <div className="close-pnl-preview">
          <span>Estimated PnL at current {position.side === 'LONG' ? 'bid' : 'ask'}</span>
          <PnlValue value={estimatedPnl}>
            <strong>{usdFormatter.format(estimatedPnl)}</strong>
          </PnlValue>
          <small>The server will execute against its latest quote when you confirm.</small>
        </div>

        <dl>
          <div><dt>Entry price</dt><dd>${priceFormatter.format(position.entryPrice)}</dd></div>
          <div><dt>Close quote</dt><dd>${priceFormatter.format(closePrice)}</dd></div>
          <div><dt>Quantity</dt><dd>{quantityFormatter.format(position.quantity)} BTC</dd></div>
          <div><dt>Position value</dt><dd>{usdFormatter.format(position.notionalUsd)}</dd></div>
        </dl>

        {error !== null && <p className="form-error" role="alert">{error}</p>}

        <div className="close-preview-actions">
          <button type="button" onClick={onCancel} disabled={pending}>Keep position</button>
          <button type="button" onClick={onConfirm} disabled={pending}>
            {pending ? 'Closing at market…' : 'Confirm market close'}
          </button>
        </div>
      </section>
    </div>
  )
}

const CLOSE_REASON_LABELS: Record<
  ClosedPaperTrade['closeReason'],
  string
> = {
  USER: 'Manual close',
  STOP_LOSS: 'Stop loss',
  TAKE_PROFIT: 'Take profit',
}

function TradeHistory({ trades }: { trades: ClosedPaperTrade[] }) {
  return (
    <section className="trades-panel history-panel">
      <header className="section-heading">
        <div>
          <span className="panel-label">Trading journal</span>
          <h2>Trade history</h2>
        </div>
        <span className="count-chip">Latest {trades.length}</span>
      </header>

      {trades.length === 0 ? (
        <div className="empty-trades compact-empty">
          <strong>No closed trades yet</strong>
          <p>Closed market positions and triggered risk controls will appear here.</p>
        </div>
      ) : (
        <div className="trades-table-wrap">
          <table className="trades-table">
            <thead>
              <tr>
                <th>Market</th>
                <th>Opened</th>
                <th>Entry / Exit</th>
                <th>Margin / Leverage</th>
                <th>Close reason</th>
                <th>Realized PnL</th>
              </tr>
            </thead>
            <tbody>
              {trades.map((trade) => (
                <tr key={trade.id}>
                  <td>
                    <strong>{trade.symbol}</strong>
                    <span className={`side-text ${trade.side === 'LONG' ? 'is-positive' : 'is-negative'}`}>{trade.side}</span>
                  </td>
                  <td>
                    {timeFormatter.format(new Date(trade.openedAt))}
                    <small>Closed {timeFormatter.format(new Date(trade.closedAt))}</small>
                  </td>
                  <td>
                    ${priceFormatter.format(trade.entryPrice)}
                    <small>${priceFormatter.format(trade.exitPrice)}</small>
                  </td>
                  <td>
                    {usdFormatter.format(trade.marginUsd)}
                    <small>{trade.leverage}× · Cross</small>
                  </td>
                  <td>{CLOSE_REASON_LABELS[trade.closeReason]}</td>
                  <td><PnlValue value={trade.realizedPnl} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export function TradingWorkspace({
  expectedUserId,
  onSessionExpired,
}: TradingWorkspaceProps) {
  const [portfolio, setPortfolio] = useState<PaperTradingPortfolio | null>(null)
  const [portfolioError, setPortfolioError] = useState<string | null>(null)
  const [isLoadingPortfolio, setIsLoadingPortfolio] = useState(true)
  const [lastUpdatedAt, setLastUpdatedAt] = useState(new Date())
  const mutationInFlightRef = useRef(false)
  const mutationControllerRef = useRef<AbortController | null>(null)
  const requestSequenceRef = useRef(0)
  const activeRef = useRef(true)

  const acceptSnapshot = useCallback((snapshot: PaperTradingPortfolio) => {
    if (snapshot.userId !== expectedUserId) {
      throw new SessionUserMismatchError()
    }
    if (!activeRef.current) {
      return
    }
    setPortfolio(snapshot)
    setPortfolioError(null)
    setLastUpdatedAt(new Date())
  }, [expectedUserId])

  useEffect(() => {
    activeRef.current = true
    return () => {
      activeRef.current = false
      mutationControllerRef.current?.abort()
      mutationControllerRef.current = null
      mutationInFlightRef.current = false
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    let requestInFlight = false

    async function refreshPortfolio() {
      if (requestInFlight || mutationInFlightRef.current) {
        return
      }

      requestInFlight = true
      const requestSequence = ++requestSequenceRef.current

      try {
        const snapshot = await getPaperTradingPortfolio(
          50,
          controller.signal,
        )

        if (
          !controller.signal.aborted &&
          requestSequence === requestSequenceRef.current
        ) {
          acceptSnapshot(snapshot)
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return
        }

        if (
          (error instanceof ApiError && error.status === 401) ||
          error instanceof SessionUserMismatchError
        ) {
          onSessionExpired()
          return
        }

        setPortfolioError(requestErrorMessage(error))
      } finally {
        requestInFlight = false

        if (!controller.signal.aborted) {
          setIsLoadingPortfolio(false)
        }
      }
    }

    void refreshPortfolio()
    const pollTimer = window.setInterval(
      () => void refreshPortfolio(),
      PORTFOLIO_POLL_INTERVAL_MS,
    )

    return () => {
      controller.abort()
      window.clearInterval(pollTimer)
    }
  }, [acceptSnapshot, onSessionExpired])

  async function runMutation(
    mutation: SnapshotMutation,
  ): Promise<PaperTradingPortfolio> {
    if (mutationInFlightRef.current) {
      throw new Error('Another paper-trading action is still in progress.')
    }

    mutationInFlightRef.current = true
    const requestSequence = ++requestSequenceRef.current
    const controller = new AbortController()
    mutationControllerRef.current = controller

    try {
      const snapshot = await mutation(controller.signal)

      if (
        !controller.signal.aborted &&
        activeRef.current &&
        requestSequence === requestSequenceRef.current
      ) {
        acceptSnapshot(snapshot)
      }

      return snapshot
    } catch (error) {
      if (controller.signal.aborted || !activeRef.current) {
        throw error
      }

      if (
        (error instanceof ApiError && error.status === 401) ||
        error instanceof SessionUserMismatchError
      ) {
        onSessionExpired()
      }

      throw error
    } finally {
      if (mutationControllerRef.current === controller) {
        mutationControllerRef.current = null
        mutationInFlightRef.current = false
      }
    }
  }

  return (
    <div className="trading-workspace">
      {portfolio !== null && (
        <AccountSummary
          account={portfolio.account}
          quote={portfolio.quote}
          lastUpdatedAt={lastUpdatedAt}
        />
      )}

      {portfolioError !== null && (
        <div className="workspace-notice" role="alert">
          <span>!</span>
          <p>
            {portfolio === null
              ? portfolioError
              : `Portfolio refresh paused: ${portfolioError}`}
          </p>
        </div>
      )}

      <div className="market-layout">
        <BtcChart
          onSessionExpired={onSessionExpired}
          openPositions={portfolio?.openPositions ?? []}
        />
        {portfolio === null ? (
          <aside className="portfolio-state-card" aria-live="polite">
            {isLoadingPortfolio ? (
              <>
                <span className="chart-loader" />
                <strong>Loading your paper account</strong>
                <p>Fetching the authoritative balance, quote, and positions…</p>
              </>
            ) : (
              <>
                <span className="empty-chart-icon">!</span>
                <strong>Trading account unavailable</strong>
                <p>{portfolioError}</p>
              </>
            )}
          </aside>
        ) : (
          <OrderTicket
            portfolio={portfolio}
            onSubmit={(request) =>
              runMutation((signal) =>
                openPaperPosition(request, expectedUserId, signal),
              )
            }
          />
        )}
      </div>

      {portfolio !== null && (
        <>
          <OpenPositions
            portfolio={portfolio}
            onUpdateRisk={(positionId, controls) =>
              runMutation((signal) =>
                updatePaperPositionRiskControls(
                  positionId,
                  controls,
                  expectedUserId,
                  signal,
                ),
              )
            }
            onClose={(positionId) =>
              runMutation((signal) =>
                closePaperPosition(
                  positionId,
                  expectedUserId,
                  signal,
                ),
              )
            }
          />
          <TradeHistory trades={portfolio.closedTrades} />
        </>
      )}
    </div>
  )
}
