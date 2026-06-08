package com.vswitch.watermeter;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DevicePreEnrollRecord(
        String serialNumber,
        String tenantId,
        String status,
        String createdAt,
        String expiresAt,
        String createdByUserId) {

    Map<String, AttributeValue> toItem() {
        return Map.ofEntries(
                Map.entry("serialNumber", AttributeValue.builder().s(serialNumber).build()),
                Map.entry("tenantId", AttributeValue.builder().s(tenantId).build()),
                Map.entry("status", AttributeValue.builder().s(status).build()),
                Map.entry("createdAt", AttributeValue.builder().s(createdAt).build()),
                Map.entry("expiresAt", AttributeValue.builder().s(expiresAt).build()),
                Map.entry(
                        "createdByUserId",
                        AttributeValue.builder().s(createdByUserId).build()));
    }
}
