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

//    @Mapping(target = "cardId", source = "id")
//    @Mapping(target = "programId", source = "program.id")
//    @Mapping(target = "programName", source = "program.name")
//    @Mapping(target = "merchantName", source = "program.merchant.name")
//    @Mapping(target = "rewardName", source = "program.rewardName")
//    @Mapping(target = "stampsCount", source = "stampsCount")
//    @Mapping(target = "stampsNeeded", expression = "java(getStampsNeeded(card))")
//    @Mapping(target = "status", expression = "java(card.getStatus().name())")
//    @Mapping(target = "hasReward", expression = "java(hasReward(card))")
//    CardItemDto cardItemDtoToCard(Card card);
//
//    List<CardItemDto> cardItemDtosToCardListResponse(List<CardItemDto> cardItemDtos);
//
//    List<CardItemDto> toDtoList(List<Card> cards);
//
//    default int getStampsNeeded(Card card) {
//        Integer ruleTotalStamps = card.getProgram().getRuleTotalStamps();
//        return ruleTotalStamps != null ? ruleTotalStamps : 10;
//    }
//
//    default boolean hasReward(Card card) {
//        return card.getStampsCount() >= getStampsNeeded(card);
//    }
}
