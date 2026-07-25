package com.raretable.casino.game.tago;

import java.util.Objects;

public final class TagoCard
{
    private final TagoCardValue value;

    public TagoCard(TagoCardValue value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Card value is required");
        }

        this.value = value;
    }

    public TagoCardValue getValue()
    {
        return value;
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }

        if (!(object instanceof TagoCard card))
        {
            return false;
        }

        return value == card.value;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }
}
