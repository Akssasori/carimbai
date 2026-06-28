package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateLocationResponse;
import com.app.carimbai.dtos.staff.admin.LocationFlags;
import com.app.carimbai.dtos.staff.admin.LocationItem;
import com.app.carimbai.models.core.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface LocationMapper {

    @Mapping(target = "merchantId", source = "merchant.id")
    CreateLocationResponse locationToCreateLocationResponse(Location location);

    /**
     * O `flags` JSONB e armazenado como String no Location. O service parseia
     * para LocationFlags via ObjectMapper e passa aqui ja resolvido.
     */
    default LocationItem toLocationItem(Location loc, LocationFlags flags) {
        if (loc == null) return null;
        return new LocationItem(
                loc.getId(),
                loc.getMerchant() != null ? loc.getMerchant().getId() : null,
                loc.getName(),
                loc.getAddress(),
                loc.getActive(),
                flags
        );
    }
}
