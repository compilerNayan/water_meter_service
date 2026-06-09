package com.vswitch.watermeter;

public record CreateUnitRequest(
        String deviceId,
        String name,
        String flatNumber,
        String floor,
        String block,
        String wing,
        String residentName,
        String phoneNumber,
        String notes) {}
