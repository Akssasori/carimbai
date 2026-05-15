package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.dtos.staff.admin.StaffItem;
import com.app.carimbai.enums.StaffRole;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.core.StaffUserMerchant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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

    @Mapping(target = "staffId", source = "staffUser.id")
    @Mapping(target = "email", source = "staffUser.email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "isDefault", source = "isDefault")
    @Mapping(target = "createdAt", source = "createdAt")
    StaffItem toStaffItem(StaffUserMerchant link);
}
