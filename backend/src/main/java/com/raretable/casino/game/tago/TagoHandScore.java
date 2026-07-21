package com.raretable.casino.game.tago;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TagoHandScore implements Comparable<TagoHandScore>
{
    private final TagoScoreType type;
    private final List<Integer> bestValuesInHalfUnits;
    private final int distanceFromPointInHalfUnits;

    private TagoHandScore(
        TagoScoreType type,
        List<Integer> bestValuesInHalfUnits,
        int distanceFromPointInHalfUnits
    )
    {
        this.type = type;
        this.bestValuesInHalfUnits = new ArrayList<>(bestValuesInHalfUnits);
        this.distanceFromPointInHalfUnits = distanceFromPointInHalfUnits;
    }

    static TagoHandScore evaluate(TagoHand hand, TagoHand point, int pointValueInHalfUnits)
    {
        if (hand == null || point == null)
        {
            throw new IllegalArgumentException("Hand and Point are required");
        }

        List<Integer> bestValues = new ArrayList<>();
        int shortestDistance = Integer.MAX_VALUE;

        for (int possibleValue : hand.getPossibleValuesInHalfUnits())
        {
            int distance = Math.abs(possibleValue - pointValueInHalfUnits);

            if (distance < shortestDistance)
            {
                bestValues.clear();
                bestValues.add(possibleValue);
                shortestDistance = distance;
            }
            else if (distance == shortestDistance)
            {
                bestValues.add(possibleValue);
            }
        }

        TagoScoreType type;

        if (hand.isZero())
        {
            type = TagoScoreType.ZERO;
        }
        else if (hand.hasSameCardsAs(point))
        {
            type = TagoScoreType.COPY_CAT;
        }
        else if (hand.isThreeOfAKind())
        {
            type = TagoScoreType.THREE_OF_A_KIND;
        }
        else if (shortestDistance == 0)
        {
            type = TagoScoreType.VALUE;
        }
        else
        {
            type = TagoScoreType.CLOSEST;
        }

        return new TagoHandScore(type, bestValues, shortestDistance);
    }

    public TagoScoreType getType()
    {
        return type;
    }

    public List<Double> getBestValues()
    {
        List<Double> values = new ArrayList<>();

        for (int halfUnits : bestValuesInHalfUnits)
        {
            values.add(halfUnits / 2.0);
        }

        return Collections.unmodifiableList(values);
    }

    public double getDistanceFromPoint()
    {
        return distanceFromPointInHalfUnits / 2.0;
    }

    @Override
    public int compareTo(TagoHandScore other)
    {
        if (other == null)
        {
            throw new IllegalArgumentException("Other score is required");
        }

        int typeComparison = Integer.compare(type.getStrength(), other.type.getStrength());

        if (typeComparison != 0)
        {
            return typeComparison;
        }

        if (type == TagoScoreType.CLOSEST)
        {
            return Integer.compare(
                other.distanceFromPointInHalfUnits,
                distanceFromPointInHalfUnits
            );
        }

        return 0;
    }
}
