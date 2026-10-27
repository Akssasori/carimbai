package com.app.carimbai.mappers;

import com.app.carimbai.config.MapStructConfig;
import com.app.carimbai.dtos.privacy.PrivacyDtos.*;
import com.app.carimbai.models.PrivacyRequest;
import com.app.carimbai.models.core.StaffUser;
import com.app.carimbai.models.fidelity.Customer;
import org.mapstruct.*;

@Mapper(config=MapStructConfig.class)
public interface PrivacyMapper {
    @Mapping(target="subjectType",constant="CUSTOMER")
    @Mapping(target="privacyOfficer",constant="false")
    Profile profile(Customer customer);
    @Mapping(target="subjectType",constant="STAFF")
    @Mapping(target="name",ignore=true) @Mapping(target="phone",ignore=true)
    Profile profile(StaffUser staff);
    RequestView view(PrivacyRequest request);
}
