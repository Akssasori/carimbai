package com.app.carimbai.dtos.staff.admin;

public record LocationItem(
        Long id,
        Long merchantId,
        String name,
        String address,
        Boolean active,
        LocationFlags flags
) {
}
