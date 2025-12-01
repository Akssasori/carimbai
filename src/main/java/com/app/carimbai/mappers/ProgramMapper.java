package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateProgramResponse;
import com.app.carimbai.models.fidelity.Program;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ProgramMapper {

    CreateProgramResponse programToCreateProgramResponse(Program program);
}
