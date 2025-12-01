package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.AdminCardResponse;
import com.app.carimbai.models.fidelity.Card;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface CardMapper {

    AdminCardResponse cardToAdminCardResponse(Card card);
}
