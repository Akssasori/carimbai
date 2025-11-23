package com.app.carimbai.dtos;

import java.util.List;

public record CardListResponse(
    List<CardItemDto> cards
) {
}
