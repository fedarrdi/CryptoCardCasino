package com.raretable.casino.game.tienlen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.raretable.casino.common.Card;
import com.raretable.casino.common.Rank;
import com.raretable.casino.common.Suit;

class TienLenCombinationTests
{
    @Test
    void spadesTwoIsTheHighestSingle()
    {
        TienLenCombination spadesTwo = TienLenCombination.create(
            List.of(card(Rank.TWO, Suit.SPADES)),
            TienLenCombinationType.SINGLE
        );
        TienLenCombination heartsTwo = TienLenCombination.create(
            List.of(card(Rank.TWO, Suit.HEARTS)),
            TienLenCombinationType.SINGLE
        );
        TienLenCombination spadesAce = TienLenCombination.create(
            List.of(card(Rank.ACE, Suit.SPADES)),
            TienLenCombinationType.SINGLE
        );

        assertTrue(spadesTwo.beats(heartsTwo));
        assertTrue(spadesTwo.beats(spadesAce));
    }

    @Test
    void validatesSameRankGroups()
    {
        TienLenCombination pair = TienLenCombination.create(
            List.of(
                card(Rank.JACK, Suit.CLUBS),
                card(Rank.JACK, Suit.SPADES)
            ),
            TienLenCombinationType.PAIR
        );
        TienLenCombination lowerPair = TienLenCombination.create(
            List.of(
                card(Rank.TEN, Suit.HEARTS),
                card(Rank.TEN, Suit.DIAMONDS)
            ),
            TienLenCombinationType.PAIR
        );
        TienLenCombination fourOfAKind = TienLenCombination.create(
            List.of(
                card(Rank.SIX, Suit.CLUBS),
                card(Rank.SIX, Suit.DIAMONDS),
                card(Rank.SIX, Suit.HEARTS),
                card(Rank.SIX, Suit.SPADES)
            ),
            TienLenCombinationType.FOUR_OF_A_KIND
        );

        assertTrue(pair.beats(lowerPair));
        assertFalse(fourOfAKind.beats(pair));
        assertThrows(
            IllegalArgumentException.class,
            () -> TienLenCombination.create(
                List.of(
                    card(Rank.QUEEN, Suit.CLUBS),
                    card(Rank.KING, Suit.CLUBS)
                ),
                TienLenCombinationType.PAIR
            )
        );
    }

    @Test
    void straightCannotIncludeTwo()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> TienLenCombination.create(
                List.of(
                    card(Rank.ACE, Suit.CLUBS),
                    card(Rank.TWO, Suit.CLUBS),
                    card(Rank.THREE, Suit.CLUBS)
                ),
                TienLenCombinationType.STRAIGHT
            )
        );
    }

    @Test
    void straightMustBeatSameLengthStraight()
    {
        TienLenCombination highStraight = TienLenCombination.create(
            List.of(
                card(Rank.SIX, Suit.CLUBS),
                card(Rank.SEVEN, Suit.HEARTS),
                card(Rank.EIGHT, Suit.SPADES)
            ),
            TienLenCombinationType.STRAIGHT
        );
        TienLenCombination lowStraight = TienLenCombination.create(
            List.of(
                card(Rank.THREE, Suit.CLUBS),
                card(Rank.FOUR, Suit.HEARTS),
                card(Rank.FIVE, Suit.SPADES)
            ),
            TienLenCombinationType.STRAIGHT
        );
        TienLenCombination longerStraight = TienLenCombination.create(
            List.of(
                card(Rank.THREE, Suit.CLUBS),
                card(Rank.FOUR, Suit.HEARTS),
                card(Rank.FIVE, Suit.SPADES),
                card(Rank.SIX, Suit.DIAMONDS)
            ),
            TienLenCombinationType.STRAIGHT
        );

        assertTrue(highStraight.beats(lowStraight));
        assertFalse(longerStraight.beats(lowStraight));
    }

    @Test
    void fullHouseComparesByTripleRank()
    {
        TienLenCombination highFullHouse = TienLenCombination.create(
            List.of(
                card(Rank.SIX, Suit.CLUBS),
                card(Rank.SIX, Suit.DIAMONDS),
                card(Rank.SIX, Suit.SPADES),
                card(Rank.THREE, Suit.CLUBS),
                card(Rank.THREE, Suit.DIAMONDS)
            ),
            TienLenCombinationType.FULL_HOUSE
        );
        TienLenCombination lowFullHouse = TienLenCombination.create(
            List.of(
                card(Rank.FIVE, Suit.CLUBS),
                card(Rank.FIVE, Suit.DIAMONDS),
                card(Rank.FIVE, Suit.SPADES),
                card(Rank.ACE, Suit.CLUBS),
                card(Rank.ACE, Suit.DIAMONDS)
            ),
            TienLenCombinationType.FULL_HOUSE
        );

        assertTrue(highFullHouse.beats(lowFullHouse));
    }

    private static Card card(Rank rank, Suit suit)
    {
        return new Card(suit, rank);
    }
}
