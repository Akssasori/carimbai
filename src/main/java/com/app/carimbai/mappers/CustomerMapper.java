package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.admin.CreateCustomerResponse;
import com.app.carimbai.dtos.admin.CreateCustomerRequest;
import com.app.carimbai.dtos.customer.CustomerLoginResponse;
import com.app.carimbai.dtos.customer.CounterCustomerRequest;
import com.app.carimbai.dtos.customer.CounterCustomerResponse;
import com.app.carimbai.models.fidelity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CustomerMapper {

    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "id", ignore = true)
    @org.mapstruct.BeanMapping(ignoreByDefault=true)
    @Mapping(target="name",source="name")
    @Mapping(target="email",source="email")
    @Mapping(target="phone",source="phone")
    Customer counterRequestToCustomer(CounterCustomerRequest request);

    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @org.mapstruct.BeanMapping(ignoreByDefault=true)
    @Mapping(target="email",source="email")
    @Mapping(target="phone",source="phone")
    Customer createRequestToCustomer(CreateCustomerRequest request);

    @Mapping(target = "customerId", source = "id")
    CounterCustomerResponse toCounterResponse(Customer customer);

    CreateCustomerResponse customerToCreateCustomerResponse(Customer customer);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "token", ignore = true)
    CustomerLoginResponse customerToCustomerLoginResponse(Customer customer);

    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "token", source = "token")
    CustomerLoginResponse customerToCustomerLoginResponse(Customer customer, String token);
}
