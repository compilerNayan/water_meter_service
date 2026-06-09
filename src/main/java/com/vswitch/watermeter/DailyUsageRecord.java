package com.vswitch.watermeter;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record DailyUsageRecord(
        String tenantId,
        String usageKey,
        String unitId,
        String name,
        String block,
        String wing,
        double totalLiters,
        int peakHour,
        double peakHourLiters,
        String updatedAt) {

    public static String usageKeyFor(String date, String deviceId) {
        return date + "#" + deviceId;
    }

    public static DailyUsageRecord fromItem(Map<String, AttributeValue> item) {
        return new DailyUsageRecord(
                stringValue(item, "tenantId"),
                stringValue(item, "usageKey"),
                stringValue(item, "unitId"),
                stringValue(item, "name"),
                stringValue(item, "block"),
                stringValue(item, "wing"),
                numberValue(item, "totalLiters"),
                (int) numberValue(item, "peakHour"),
                numberValue(item, "peakHourLiters"),
                stringValue(item, "updatedAt"));
    }

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("usageKey", AttributeValue.builder().s(usageKey).build());
        item.put("unitId", AttributeValue.builder().s(unitId).build());
        item.put("name", AttributeValue.builder().s(name).build());
        item.put("block", AttributeValue.builder().s(block).build());
        item.put("wing", AttributeValue.builder().s(wing).build());
        item.put("totalLiters", AttributeValue.builder().n(Double.toString(totalLiters)).build());
        item.put("peakHour", AttributeValue.builder().n(Integer.toString(peakHour)).build());
        item.put(
                "peakHourLiters",
                AttributeValue.builder().n(Double.toString(peakHourLiters)).build());
        item.put("updatedAt", AttributeValue.builder().s(updatedAt).build());
        return item;
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static double numberValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return 0;
        }
        return Double.parseDouble(value.n());
    }
}
