package com.raretable.casino.table;

import jakarta.validation.constraints.NotBlank;

public record JoinTableRequest(@NotBlank String name)
{
}
