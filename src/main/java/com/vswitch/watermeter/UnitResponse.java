package com.vswitch.watermeter;

public record UnitResponse(
        String id,
        String name,
        String deviceId,
        String flatNumber,
        String floor,
        String block,
        String wing,
        String residentName,
        String phoneNumber,
        String notes,
        String enrollmentStatus,
        String unitInviteCode) {}
