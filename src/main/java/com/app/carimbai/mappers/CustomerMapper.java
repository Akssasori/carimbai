package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.models.fidelity.Customer;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface CustomerMapper {

    CreateCustomerResponse customerToCreateCustomerResponse(Customer customer);
}
