package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.models.core.Location;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface LocationMapper {

    CreateLocationResponse locationToCreateLocationResponse(Location location);
}
