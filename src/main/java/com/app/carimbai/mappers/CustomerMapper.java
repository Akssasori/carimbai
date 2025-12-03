package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.dtos.customer.CustomerLoginResponse;
import com.app.carimbai.models.fidelity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CustomerMapper {

    CreateCustomerResponse customerToCreateCustomerResponse(Customer customer);
    @Mapping(target = "customerId", source = "customer.id")
    CustomerLoginResponse customerToCustomerLoginResponse(Customer customer, boolean created);
}
