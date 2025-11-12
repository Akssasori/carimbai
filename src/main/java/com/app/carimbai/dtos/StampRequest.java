package com.app.carimbai.dtos;

import com.app.carimbai.enums.StampType;
import jakarta.validation.constraints.NotNull;

public record StampRequest(@NotNull StampType type, @NotNull Object payload) {
}
