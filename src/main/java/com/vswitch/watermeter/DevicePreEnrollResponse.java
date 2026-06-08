package com.vswitch.watermeter;

public record DevicePreEnrollResponse(
        String tenantId,
        String serialNumber,
        String status,
        String expiresAt) {}
