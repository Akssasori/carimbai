package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.dtos.admin.CreateStaffUserResponse;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Program;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface StaffMapper {

    CreateStaffUserResponse staffUserToCreateStaffUserResponse(StaffUser staffUser);
}
