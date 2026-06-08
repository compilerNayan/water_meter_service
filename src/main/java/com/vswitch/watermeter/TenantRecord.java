package com.vswitch.watermeter;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record TenantRecord(
        String tenantId,
        String name,
        String ownerUserId,
        String structure,
        String createdAt,
        String updatedAt) {

    static TenantRecord fromItem(Map<String, AttributeValue> item) {
        return new TenantRecord(
                stringValue(item, "tenantId"),
                stringValue(item, "name"),
                stringValue(item, "ownerUserId"),
                stringValue(item, "structure"),
                stringValue(item, "createdAt"),
                stringValue(item, "updatedAt"));
    }

    Map<String, AttributeValue> toItem() {
        return Map.ofEntries(
                Map.entry("tenantId", AttributeValue.builder().s(tenantId).build()),
                Map.entry("name", AttributeValue.builder().s(name).build()),
                Map.entry("ownerUserId", AttributeValue.builder().s(ownerUserId).build()),
                Map.entry("structure", AttributeValue.builder().s(structure).build()),
                Map.entry("createdAt", AttributeValue.builder().s(createdAt).build()),
                Map.entry("updatedAt", AttributeValue.builder().s(updatedAt).build()));
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }
}
