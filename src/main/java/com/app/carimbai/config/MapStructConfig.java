package com.app.carimbai.config;

import com.app.carimbai.mappers.CardMapper;
import com.app.carimbai.mappers.CustomerMapper;
import com.app.carimbai.mappers.LocationMapper;
import com.app.carimbai.mappers.MerchantMapper;
import com.app.carimbai.mappers.ProgramMapper;
import com.app.carimbai.mappers.StaffMapper;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

@MapperConfig(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        uses = {
                MerchantMapper.class,
                LocationMapper.class,
                ProgramMapper.class,
                StaffMapper.class,
                CustomerMapper.class,
                CardMapper.class
        }
)
public interface MapStructConfig {
}
