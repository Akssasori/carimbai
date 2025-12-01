package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.models.core.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface LocationMapper {

    @Mapping(target = "merchantId", source = "merchant.id")
    CreateLocationResponse locationToCreateLocationResponse(Location location);
}
