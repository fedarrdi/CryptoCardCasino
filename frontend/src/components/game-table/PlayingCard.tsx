import { Club, Diamond, Heart, Spade, type LucideIcon } from 'lucide-react'
import type { CardSuit, VisualCard } from './types.ts'

type CardSize = 'standard' | 'small'

type PlayingCardProps =
  | {
      variant: 'back'
      size?: CardSize
    }
  | {
      variant: 'face'
      card: VisualCard
      size?: CardSize
    }

const SUIT_ICONS: Record<CardSuit, LucideIcon> = {
  clubs: Club,
  diamonds: Diamond,
  hearts: Heart,
  spades: Spade,
}

function PlayingCard(props: PlayingCardProps) {
  const sizeClass = props.size === 'small' ? 'is-small' : ''

  if (props.variant === 'back') {
    return (
      <span className={`table-card table-card-back ${sizeClass}`} aria-hidden="true">
        <span className="table-card-back-pattern">
          <Diamond fill="currentColor" />
        </span>
      </span>
    )
  }

  const { card } = props

  const SuitIcon = SUIT_ICONS[card.suit]
  const colorClass = card.suit === 'diamonds' || card.suit === 'hearts' ? 'is-red' : 'is-black'

  return (
    <span
      className={`table-card table-card-face ${colorClass} ${sizeClass}`}
      role="img"
      aria-label={`${card.rank} of ${card.suit}`}
    >
      <span className="table-card-corner">
        <strong>{card.rank}</strong>
        <SuitIcon fill="currentColor" />
      </span>
      <SuitIcon className="table-card-suit" fill="currentColor" />
      <span className="table-card-corner table-card-corner-bottom">
        <strong>{card.rank}</strong>
        <SuitIcon fill="currentColor" />
      </span>
    </span>
  )
}

export default PlayingCard
