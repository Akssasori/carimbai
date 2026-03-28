package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.StaffUser;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface StaffMapper {

    default CreateStaffUserResponse toCreateStaffUserResponse(StaffUser user, Long merchantId, StaffRole role) {
        return new CreateStaffUserResponse(
                user.getId(),
                user.getEmail(),
                merchantId,
                role
        );
    }
}
