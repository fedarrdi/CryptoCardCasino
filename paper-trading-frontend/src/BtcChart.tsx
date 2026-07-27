import { useEffect, useRef, useState } from 'react'
import {
  CandlestickSeries,
  ColorType,
  CrosshairMode,
  createChart,
  type CandlestickData,
  type ISeriesApi,
  type LogicalRangeChangeEventHandler,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts'

import {
  ApiError,
  getBtcCandles,
  getCurrentUser,
  type BtcCandleUpdate,
  type MarketCandle,
} from './api.ts'

type HistoryStatus = 'loading' | 'ready' | 'empty' | 'error'
type StreamStatus = 'connecting' | 'live' | 'reconnecting' | 'waiting'
type OlderHistoryStatus = 'idle' | 'loading' | 'exhausted' | 'error'

type BtcChartProps = {
  onSessionExpired: () => void
}

const SOCKET_CONNECT_TIMEOUT_MS = 10_000
const STREAM_STALE_TIMEOUT_MS = 20_000
const SESSION_REVALIDATION_MS = 5 * 60_000
const OLDER_HISTORY_PAGE_SIZE = 1_000
const OLDER_HISTORY_LOAD_THRESHOLD = 100

const priceFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const volumeFormatter = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 2,
})

function toChartCandle(candle: MarketCandle): CandlestickData<Time> {
  return {
    time: candle.time as UTCTimestamp,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}

function marketSocketUrl(): string {
  const url = new URL('/ws/market-data/btcusdt/1h', window.location.href)
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function chartErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }

  return 'Could not load BTC candle history.'
}

function isTransientSyncError(error: unknown): boolean {
  return (
    error instanceof TypeError ||
    (error instanceof ApiError && error.status >= 500)
  )
}

function statusLabel(
  historyStatus: HistoryStatus,
  streamStatus: StreamStatus,
): string {
  if (historyStatus === 'loading') {
    return streamStatus === 'reconnecting'
      ? 'Retrying sync'
      : 'Loading history'
  }

  if (historyStatus === 'error') {
    return 'Unavailable'
  }

  if (historyStatus === 'empty') {
    return 'Waiting for data'
  }

  if (streamStatus === 'live') {
    return 'Live'
  }

  if (streamStatus === 'connecting') {
    return 'Connecting'
  }

  return streamStatus === 'reconnecting'
    ? 'Reconnecting'
    : 'Waiting for update'
}

function CandleValue({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <span>
      <small>{label}</small>
      <strong>{value}</strong>
    </span>
  )
}

export function BtcChart({ onSessionExpired }: BtcChartProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [historyStatus, setHistoryStatus] =
    useState<HistoryStatus>('loading')
  const [streamStatus, setStreamStatus] =
    useState<StreamStatus>('connecting')
  const [latestCandle, setLatestCandle] = useState<MarketCandle | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [olderHistoryStatus, setOlderHistoryStatus] =
    useState<OlderHistoryStatus>('idle')
  const [olderHistoryError, setOlderHistoryError] = useState<string | null>(
    null,
  )
  const [reloadKey, setReloadKey] = useState(0)
  const loadOlderHistoryRef = useRef<() => void>(() => undefined)

  useEffect(() => {
    const container = containerRef.current

    if (container === null) {
      throw new Error('The BTC chart container is unavailable.')
    }

    let disposed = false
    let socket: WebSocket | null = null
    let historyController: AbortController | null = null
    let olderHistoryController: AbortController | null = null
    let reconnectTimer: number | null = null
    let socketConnectTimer: number | null = null
    let streamStaleTimer: number | null = null
    let olderHistoryArmFrame: number | null = null
    let reconnectAttempt = 0
    let cycleId = 0
    let hasLoadedHistory = false
    let olderHistoryLoadingArmed = false
    let olderHistoryInFlight = false
    let hasMoreHistory = false
    let nextBefore: number | null = null
    let sessionValidationInFlight = false
    const candlesByTime = new Map<number, MarketCandle>()
    const closedCandleTimes = new Set<number>()
    const sessionController = new AbortController()

    setHistoryStatus('loading')
    setStreamStatus('connecting')
    setLatestCandle(null)
    setError(null)
    setOlderHistoryStatus('idle')
    setOlderHistoryError(null)

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        attributionLogo: true,
        background: {
          type: ColorType.Solid,
          color: '#101310',
        },
        textColor: '#747b72',
        fontFamily: "'DM Mono', monospace",
      },
      grid: {
        vertLines: { color: '#1c211c' },
        horzLines: { color: '#1c211c' },
      },
      crosshair: {
        mode: CrosshairMode.Normal,
        vertLine: {
          color: '#66705f',
          labelBackgroundColor: '#2c332a',
        },
        horzLine: {
          color: '#66705f',
          labelBackgroundColor: '#2c332a',
        },
      },
      rightPriceScale: {
        borderColor: '#2a2f2a',
        scaleMargins: {
          top: 0.12,
          bottom: 0.12,
        },
      },
      timeScale: {
        borderColor: '#2a2f2a',
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 3,
      },
      localization: {
        priceFormatter: (price: number) => priceFormatter.format(price),
      },
    })

    const candleSeries: ISeriesApi<'Candlestick'> = chart.addSeries(
      CandlestickSeries,
      {
        upColor: '#c0f25d',
        downColor: '#ff705c',
        borderUpColor: '#c0f25d',
        borderDownColor: '#ff705c',
        wickUpColor: '#c0f25d',
        wickDownColor: '#ff705c',
        priceFormat: {
          type: 'price',
          precision: 2,
          minMove: 0.01,
        },
      },
    )

    function orderedCandles(): MarketCandle[] {
      return [...candlesByTime.values()].sort(
        (left, right) => left.time - right.time,
      )
    }

    function validateHistoryPageMetadata(
      history: Awaited<ReturnType<typeof getBtcCandles>>,
    ) {
      const firstCandleTime = history.candles.at(0)?.time

      if (
        history.hasMore &&
        (firstCandleTime === undefined ||
          history.nextBefore !== firstCandleTime)
      ) {
        throw new Error(
          'The candle-history cursor must equal the oldest returned candle.',
        )
      }
    }

    function applyHistoryPageMetadata(
      history: Awaited<ReturnType<typeof getBtcCandles>>,
    ) {
      validateHistoryPageMetadata(history)

      hasMoreHistory = history.hasMore
      nextBefore = history.nextBefore
      setOlderHistoryStatus(history.hasMore ? 'idle' : 'exhausted')
      setOlderHistoryError(null)
    }

    async function loadOlderHistory() {
      if (
        disposed ||
        !olderHistoryLoadingArmed ||
        olderHistoryInFlight ||
        !hasMoreHistory
      ) {
        return
      }

      if (nextBefore === null) {
        throw new Error(
          'Cannot load older candles without a pagination cursor.',
        )
      }

      const requestedBefore = nextBefore
      const controller = new AbortController()
      olderHistoryController = controller
      olderHistoryInFlight = true
      setOlderHistoryStatus('loading')
      setOlderHistoryError(null)

      try {
        const history = await getBtcCandles({
          before: requestedBefore,
          limit: OLDER_HISTORY_PAGE_SIZE,
          signal: controller.signal,
        })

        if (disposed || controller.signal.aborted) {
          return
        }

        validateHistoryPageMetadata(history)

        if (
          history.candles.some((candle) => candle.time >= requestedBefore)
        ) {
          throw new Error(
            'The candle-history response contains a candle outside the requested page.',
          )
        }

        if (
          history.hasMore &&
          (history.nextBefore === null ||
            history.nextBefore >= requestedBefore)
        ) {
          throw new Error(
            'The candle-history pagination cursor did not move backwards.',
          )
        }

        const visibleRange = chart.timeScale().getVisibleLogicalRange()
        const previouslyOrdered = orderedCandles()
        const previousOldestTime = previouslyOrdered.at(0)?.time
        let insertedBefore = 0

        for (const candle of history.candles) {
          if (
            !candlesByTime.has(candle.time) &&
            (previousOldestTime === undefined ||
              candle.time < previousOldestTime)
          ) {
            insertedBefore += 1
          }

          candlesByTime.set(candle.time, candle)
          closedCandleTimes.add(candle.time)
        }

        candleSeries.setData(orderedCandles().map(toChartCandle))

        if (visibleRange !== null && insertedBefore > 0) {
          chart.timeScale().setVisibleLogicalRange({
            from: visibleRange.from + insertedBefore,
            to: visibleRange.to + insertedBefore,
          })
        }

        applyHistoryPageMetadata(history)
      } catch (requestError) {
        if (disposed || controller.signal.aborted) {
          return
        }

        if (
          requestError instanceof ApiError &&
          requestError.status === 401
        ) {
          onSessionExpired()
          return
        }

        setOlderHistoryError(chartErrorMessage(requestError))
        setOlderHistoryStatus('error')
      } finally {
        if (olderHistoryController === controller) {
          olderHistoryController = null
        }

        olderHistoryInFlight = false
      }
    }

    loadOlderHistoryRef.current = () => {
      void loadOlderHistory()
    }

    const onVisibleLogicalRangeChange: LogicalRangeChangeEventHandler = (
      visibleRange,
    ) => {
      if (
        visibleRange === null ||
        !olderHistoryLoadingArmed ||
        olderHistoryInFlight ||
        !hasMoreHistory
      ) {
        return
      }

      const bars = candleSeries.barsInLogicalRange(visibleRange)

      if (
        bars !== null &&
        bars.barsBefore < OLDER_HISTORY_LOAD_THRESHOLD
      ) {
        void loadOlderHistory()
      }
    }

    chart
      .timeScale()
      .subscribeVisibleLogicalRangeChange(onVisibleLogicalRangeChange)

    function clearSocketTimers() {
      if (socketConnectTimer !== null) {
        window.clearTimeout(socketConnectTimer)
        socketConnectTimer = null
      }

      if (streamStaleTimer !== null) {
        window.clearTimeout(streamStaleTimer)
        streamStaleTimer = null
      }
    }

    function scheduleReconnect() {
      if (disposed) {
        return
      }

      setStreamStatus('reconnecting')
      const delay = Math.min(1_000 * 2 ** reconnectAttempt, 30_000)
      reconnectAttempt += 1
      reconnectTimer = window.setTimeout(() => {
        reconnectTimer = null
        synchronize(true)
      }, delay)
    }

    function synchronize(isReconnect: boolean) {
      if (disposed) {
        return
      }

      clearSocketTimers()
      const currentCycleId = ++cycleId
      const bufferedCandles = new Map<number, BtcCandleUpdate>()
      const currentHistoryController = new AbortController()
      let historyApplied = false
      let socketOpened = false
      let latestAppliedTime: number | null = null
      let latestAppliedClosed = false

      historyController = currentHistoryController
      setStreamStatus(isReconnect ? 'reconnecting' : 'connecting')

      const currentSocket = new WebSocket(marketSocketUrl())
      socket = currentSocket

      function isCurrentCycle(): boolean {
        return !disposed && currentCycleId === cycleId
      }

      function resetStaleCountdown() {
        if (streamStaleTimer !== null) {
          window.clearTimeout(streamStaleTimer)
        }

        streamStaleTimer = window.setTimeout(() => {
          streamStaleTimer = null

          if (isCurrentCycle() && socketOpened && historyApplied) {
            setStreamStatus('waiting')
          }
        }, STREAM_STALE_TIMEOUT_MS)
      }

      function applyLiveCandle(candle: BtcCandleUpdate): boolean {
        if (
          latestAppliedTime !== null &&
          (candle.time < latestAppliedTime ||
            (candle.time === latestAppliedTime &&
              latestAppliedClosed &&
              !candle.closed))
        ) {
          return false
        }

        candlesByTime.set(candle.time, candle)

        if (candle.closed) {
          closedCandleTimes.add(candle.time)
        }

        candleSeries.update(toChartCandle(candle))
        latestAppliedTime = candle.time
        latestAppliedClosed = candle.closed
        return true
      }

      function bufferLiveCandle(candle: BtcCandleUpdate) {
        const bufferedCandle = bufferedCandles.get(candle.time)

        if (
          bufferedCandle !== undefined &&
          bufferedCandle.closed &&
          !candle.closed
        ) {
          return
        }

        bufferedCandles.set(candle.time, candle)
      }

      function markLiveWhenSynchronized() {
        if (!isCurrentCycle() || !socketOpened || !historyApplied) {
          return
        }

        reconnectAttempt = 0
        setStreamStatus('live')
        resetStaleCountdown()
      }

      currentSocket.onopen = () => {
        if (socketConnectTimer !== null) {
          window.clearTimeout(socketConnectTimer)
          socketConnectTimer = null
        }

        socketOpened = true
        resetStaleCountdown()
        markLiveWhenSynchronized()
      }

      currentSocket.onmessage = (event) => {
        if (!isCurrentCycle()) {
          return
        }

        const candle = JSON.parse(event.data as string) as BtcCandleUpdate

        if (!historyApplied) {
          bufferLiveCandle(candle)
          return
        }

        if (!applyLiveCandle(candle)) {
          return
        }

        setLatestCandle(candle)
        setHistoryStatus('ready')
        setStreamStatus('live')
        resetStaleCountdown()
      }

      currentSocket.onerror = () => {
        currentSocket.close()
      }

      currentSocket.onclose = () => {
        if (!isCurrentCycle()) {
          return
        }

        clearSocketTimers()

        if (socket === currentSocket) {
          socket = null
        }

        currentHistoryController.abort()
        scheduleReconnect()
      }

      socketConnectTimer = window.setTimeout(() => {
        socketConnectTimer = null

        if (isCurrentCycle() && !socketOpened) {
          currentSocket.close()
        }
      }, SOCKET_CONNECT_TIMEOUT_MS)

      async function reconcileHistory() {
        try {
          const history = await getBtcCandles({
            signal: currentHistoryController.signal,
          })

          if (!isCurrentCycle()) {
            return
          }

          const shouldEstablishInitialViewport = !hasLoadedHistory

          if (shouldEstablishInitialViewport) {
            applyHistoryPageMetadata(history)
          }

          for (const candle of history.candles) {
            candlesByTime.set(candle.time, candle)
            closedCandleTimes.add(candle.time)
          }

          const synchronizedCandles = orderedCandles()
          const latestSynchronizedCandle =
            synchronizedCandles.at(-1) ?? null
          const bufferedUpdates = [...bufferedCandles.values()].sort(
            (left, right) => left.time - right.time,
          )
          let newestCandle: MarketCandle | null = latestSynchronizedCandle

          candleSeries.setData(synchronizedCandles.map(toChartCandle))
          latestAppliedTime = latestSynchronizedCandle?.time ?? null
          latestAppliedClosed =
            latestAppliedTime !== null &&
            closedCandleTimes.has(latestAppliedTime)

          for (const candle of bufferedUpdates) {
            if (applyLiveCandle(candle)) {
              newestCandle = candle
            }
          }

          bufferedCandles.clear()
          historyApplied = true
          historyController = null
          hasLoadedHistory = true
          setLatestCandle(newestCandle)
          setHistoryStatus(newestCandle === null ? 'empty' : 'ready')

          if (shouldEstablishInitialViewport && newestCandle !== null) {
            chart.timeScale().resetTimeScale()
            olderHistoryArmFrame = window.requestAnimationFrame(() => {
              olderHistoryArmFrame = null

              if (!disposed) {
                olderHistoryLoadingArmed = true
              }
            })
          } else if (newestCandle !== null) {
            olderHistoryLoadingArmed = true
          }

          markLiveWhenSynchronized()
        } catch (requestError) {
          if (currentHistoryController.signal.aborted || !isCurrentCycle()) {
            return
          }

          clearSocketTimers()
          currentSocket.onopen = null
          currentSocket.onmessage = null
          currentSocket.onerror = null
          currentSocket.onclose = null

          if (
            currentSocket.readyState === WebSocket.CONNECTING ||
            currentSocket.readyState === WebSocket.OPEN
          ) {
            currentSocket.close(1000, 'History unavailable')
          }

          if (socket === currentSocket) {
            socket = null
          }

          historyController = null

          if (
            requestError instanceof ApiError &&
            requestError.status === 401
          ) {
            onSessionExpired()
            return
          }

          if (isTransientSyncError(requestError)) {
            setError(null)

            if (!hasLoadedHistory) {
              setHistoryStatus('loading')
            }

            scheduleReconnect()
            return
          }

          setError(chartErrorMessage(requestError))
          setHistoryStatus('error')
        }
      }

      void reconcileHistory()
    }

    async function revalidateSession() {
      if (disposed || sessionValidationInFlight) {
        return
      }

      sessionValidationInFlight = true

      try {
        await getCurrentUser(sessionController.signal)
      } catch (requestError) {
        if (
          !sessionController.signal.aborted &&
          requestError instanceof ApiError &&
          requestError.status === 401
        ) {
          onSessionExpired()
        }
      } finally {
        sessionValidationInFlight = false
      }
    }

    synchronize(false)
    const sessionTimer = window.setInterval(() => {
      void revalidateSession()
    }, SESSION_REVALIDATION_MS)

    return () => {
      disposed = true
      cycleId += 1
      historyController?.abort()
      olderHistoryController?.abort()
      sessionController.abort()
      window.clearInterval(sessionTimer)
      clearSocketTimers()
      loadOlderHistoryRef.current = () => undefined

      if (olderHistoryArmFrame !== null) {
        window.cancelAnimationFrame(olderHistoryArmFrame)
      }

      chart
        .timeScale()
        .unsubscribeVisibleLogicalRangeChange(onVisibleLogicalRangeChange)

      if (reconnectTimer !== null) {
        window.clearTimeout(reconnectTimer)
      }

      if (socket !== null) {
        socket.onopen = null
        socket.onmessage = null
        socket.onerror = null
        socket.onclose = null
        socket.close(1000, 'Chart closed')
      }

      chart.remove()
    }
  }, [onSessionExpired, reloadKey])

  const displayedStatus = statusLabel(historyStatus, streamStatus)
  const isLive = historyStatus === 'ready' && streamStatus === 'live'

  return (
    <section className="chart-card">
      <header className="chart-heading">
        <div>
          <span className="panel-label">BTC / USDT · Spot market</span>
          <div className="chart-title-row">
            <h2>Bitcoin price</h2>
            <span className="timeframe-chip">1H</span>
          </div>
        </div>
        <span
          className={`chart-status ${isLive ? 'is-live' : ''}`}
          aria-live="polite"
        >
          <i />
          {displayedStatus}
        </span>
      </header>

      <div className="chart-readout" aria-label="Latest BTC candle values">
        <CandleValue
          label="Open"
          value={
            latestCandle === null
              ? '—'
              : priceFormatter.format(latestCandle.open)
          }
        />
        <CandleValue
          label="High"
          value={
            latestCandle === null
              ? '—'
              : priceFormatter.format(latestCandle.high)
          }
        />
        <CandleValue
          label="Low"
          value={
            latestCandle === null
              ? '—'
              : priceFormatter.format(latestCandle.low)
          }
        />
        <CandleValue
          label="Close"
          value={
            latestCandle === null
              ? '—'
              : priceFormatter.format(latestCandle.close)
          }
        />
        <CandleValue
          label="Volume"
          value={
            latestCandle === null
              ? '—'
              : volumeFormatter.format(latestCandle.volume)
          }
        />
      </div>

      <div className="chart-stage">
        <div
          className="chart-canvas"
          ref={containerRef}
          role="img"
          aria-label="Interactive one-hour BTC USDT candlestick chart"
        />

        {historyStatus === 'ready' &&
          olderHistoryStatus === 'loading' && (
            <div className="older-history-status" role="status">
              <span className="older-history-spinner" />
              Loading older candles
            </div>
          )}

        {historyStatus === 'ready' &&
          olderHistoryStatus === 'error' && (
            <div
              className="older-history-status older-history-error"
              role="alert"
            >
              <span>{olderHistoryError}</span>
              <button
                type="button"
                onClick={() => loadOlderHistoryRef.current()}
              >
                Retry
              </button>
            </div>
          )}

        {historyStatus === 'loading' && (
          <div className="chart-overlay" role="status">
            <span className="chart-loader" />
            <strong>
              {streamStatus === 'reconnecting'
                ? 'Retrying candle sync'
                : 'Loading candle history'}
            </strong>
            <small>
              {streamStatus === 'reconnecting'
                ? 'The market-data service is temporarily unavailable.'
                : 'Preparing the 1-hour BTC / USDT market view…'}
            </small>
          </div>
        )}

        {historyStatus === 'empty' && (
          <div className="chart-overlay" role="status">
            <span className="empty-chart-icon">╱╲</span>
            <strong>No candle history yet</strong>
            <small>Waiting for the first live one-hour candle.</small>
          </div>
        )}

        {historyStatus === 'error' && (
          <div className="chart-overlay chart-error" role="alert">
            <span className="empty-chart-icon">!</span>
            <strong>Chart unavailable</strong>
            <small>{error}</small>
            <button type="button" onClick={() => setReloadKey((key) => key + 1)}>
              Try again
            </button>
          </div>
        )}
      </div>

      <div className="chart-footer">
        <span>Binance 1-hour trade-price candles</span>
        <span>Scroll to zoom · drag left to load history</span>
      </div>
    </section>
  )
}

export function LockedBtcChart() {
  return (
    <section className="chart-card chart-card-locked">
      <header className="chart-heading">
        <div>
          <span className="panel-label">BTC / USDT · Spot market</span>
          <div className="chart-title-row">
            <h2>Bitcoin price</h2>
            <span className="timeframe-chip">1H</span>
          </div>
        </div>
        <span className="chart-status">
          <i />
          Locked
        </span>
      </header>

      <div className="locked-chart-stage">
        <span className="chart-lock-icon" aria-hidden="true">◇</span>
        <strong>Connect your wallet to open the chart</strong>
        <p>
          Historical one-hour candles and the live BTC market stream are
          available inside an authenticated session.
        </p>
      </div>
    </section>
  )
}
