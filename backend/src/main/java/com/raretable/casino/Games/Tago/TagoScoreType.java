package com.raretable.casino.Games.Tago;

public enum TagoScoreType
{
    CLOSEST(1),
    VALUE(2),
    THREE_OF_A_KIND(3),
    COPY_CAT(4),
    ZERO(5);

    private final int strength;

    TagoScoreType(int strength)
    {
        this.strength = strength;
    }

    int getStrength()
    {
        return strength;
    }
}
