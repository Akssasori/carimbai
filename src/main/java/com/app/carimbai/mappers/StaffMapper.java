package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Program;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface StaffMapper {

    @Mapping(target = "merchantId", source = "merchant.id")
    @Mapping(target = "password", source = "passwordHash")
    CreateStaffUserResponse staffUserToCreateStaffUserResponse(StaffUser staffUser);
}
