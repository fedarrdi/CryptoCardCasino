package com.raretable.casino.Games.Tago;

public enum TagoCardValue
{
    // Half-units keep every TAGO value exact without floating-point calculations.
    ZERO(0),
    HALF(1),
    ONE(2),
    TWO(4),
    THREE(6),
    FOUR(8),
    FIVE(10),
    SIX(12),
    SEVEN(14),
    EIGHT(16);

    private final int halfUnits;

    TagoCardValue(int halfUnits)
    {
        this.halfUnits = halfUnits;
    }

    public double getNumericValue()
    {
        return halfUnits / 2.0;
    }

    int getHalfUnits()
    {
        return halfUnits;
    }
}
