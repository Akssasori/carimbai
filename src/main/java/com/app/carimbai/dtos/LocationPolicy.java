package com.app.carimbai.dtos;

public record LocationPolicy(
        boolean requirePinOnRedeem,
        boolean enableScanA,
        boolean enableScanB
) {
    public static LocationPolicy defaults() {
        return new LocationPolicy(true, true, false);
    }

}
