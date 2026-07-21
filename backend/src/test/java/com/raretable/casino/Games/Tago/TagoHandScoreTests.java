package com.raretable.casino.Games.Tago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TagoHandScoreTests
{
    @Test
    void pairCanUseItsTotalOrVoidThePair()
    {
        TagoHand hand = hand(TagoCardValue.ONE, TagoCardValue.EIGHT, TagoCardValue.EIGHT);

        assertEquals(List.of(17.0, 1.0), hand.getPossibleValues());
    }

    @Test
    void pairAutomaticallyUsesTheValueClosestToThePoint()
    {
        TagoHand point = hand(TagoCardValue.ZERO, TagoCardValue.ZERO, TagoCardValue.TWO);
        TagoHand playerHand = hand(TagoCardValue.ONE, TagoCardValue.EIGHT, TagoCardValue.EIGHT);

        TagoHandScore score = TagoHandScore.evaluate(playerHand, point, 4);

        assertEquals(TagoScoreType.CLOSEST, score.getType());
        assertEquals(List.of(1.0), score.getBestValues());
        assertEquals(1.0, score.getDistanceFromPoint());
    }

    @Test
    void copyCatMatchesThePointRegardlessOfCardOrder()
    {
        TagoHand point = hand(TagoCardValue.ONE, TagoCardValue.THREE, TagoCardValue.SEVEN);
        TagoHand playerHand = hand(TagoCardValue.SEVEN, TagoCardValue.ONE, TagoCardValue.THREE);

        TagoHandScore score = TagoHandScore.evaluate(playerHand, point, 22);

        assertEquals(TagoScoreType.COPY_CAT, score.getType());
    }

    @Test
    void scoreHierarchyFollowsTheOfficialOrder()
    {
        TagoHand point = hand(TagoCardValue.ONE, TagoCardValue.TWO, TagoCardValue.THREE);
        TagoHandScore zero = TagoHandScore.evaluate(
            hand(TagoCardValue.ZERO, TagoCardValue.ZERO, TagoCardValue.ZERO),
            point,
            12
        );
        TagoHandScore copyCat = TagoHandScore.evaluate(
            hand(TagoCardValue.THREE, TagoCardValue.ONE, TagoCardValue.TWO),
            point,
            12
        );
        TagoHandScore threeOfAKind = TagoHandScore.evaluate(
            hand(TagoCardValue.FOUR, TagoCardValue.FOUR, TagoCardValue.FOUR),
            point,
            12
        );
        TagoHandScore value = TagoHandScore.evaluate(
            hand(TagoCardValue.ZERO, TagoCardValue.TWO, TagoCardValue.FOUR),
            point,
            12
        );
        TagoHandScore closest = TagoHandScore.evaluate(
            hand(TagoCardValue.ZERO, TagoCardValue.TWO, TagoCardValue.THREE),
            point,
            12
        );

        assertTrue(zero.compareTo(copyCat) > 0);
        assertTrue(copyCat.compareTo(threeOfAKind) > 0);
        assertTrue(threeOfAKind.compareTo(value) > 0);
        assertTrue(value.compareTo(closest) > 0);
    }

    @Test
    void allNonZeroThreeOfAKindHandsTie()
    {
        TagoHand point = hand(TagoCardValue.ZERO, TagoCardValue.TWO, TagoCardValue.FIVE);
        TagoHandScore threes = TagoHandScore.evaluate(
            hand(TagoCardValue.THREE, TagoCardValue.THREE, TagoCardValue.THREE),
            point,
            14
        );
        TagoHandScore eights = TagoHandScore.evaluate(
            hand(TagoCardValue.EIGHT, TagoCardValue.EIGHT, TagoCardValue.EIGHT),
            point,
            14
        );

        assertEquals(0, threes.compareTo(eights));
    }

    private TagoHand hand(
        TagoCardValue hidden,
        TagoCardValue firstVisible,
        TagoCardValue secondVisible
    )
    {
        return new TagoHand(
            new TagoCard(hidden),
            new TagoCard(firstVisible),
            new TagoCard(secondVisible)
        );
    }
}
