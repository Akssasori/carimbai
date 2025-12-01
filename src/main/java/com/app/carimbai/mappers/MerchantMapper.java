package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateMerchantResponse;
import com.app.carimbai.models.core.Merchant;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface MerchantMapper {

    CreateMerchantResponse merchanToCreateMerchantResponse(Merchant merchant);
}
