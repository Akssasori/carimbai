package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.AdminCardResponse;
import com.app.carimbai.models.fidelity.Card;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CardMapper {

    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "customerId", source = "customer.id")
    AdminCardResponse cardToAdminCardResponse(Card card);
}
