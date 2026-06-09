package com.vswitch.watermeter;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record MinuteUsageRecord(
        String deviceId,
        String minuteKey,
        String tenantId,
        double volumeLiters,
        double avgFlowRateLpm,
        double valveTargetPercent,
        long expiresAt) {

    static String minuteKeyFor(java.time.Instant instant) {
        return "minute#" + instant.toString();
    }

    static MinuteUsageRecord fromItem(Map<String, AttributeValue> item) {
        return new MinuteUsageRecord(
                stringValue(item, "deviceId"),
                stringValue(item, "minuteKey"),
                stringValue(item, "tenantId"),
                numberValue(item, "volumeLiters"),
                numberValue(item, "avgFlowRateLpm"),
                numberValue(item, "valveTargetPercent", 100),
                longValue(item, "expiresAt"));
    }

    Map<String, AttributeValue> toItem() {
        return Map.of(
                "deviceId", AttributeValue.builder().s(deviceId).build(),
                "minuteKey", AttributeValue.builder().s(minuteKey).build(),
                "tenantId", AttributeValue.builder().s(tenantId).build(),
                "volumeLiters", AttributeValue.builder().n(Double.toString(volumeLiters)).build(),
                "avgFlowRateLpm", AttributeValue.builder().n(Double.toString(avgFlowRateLpm)).build(),
                "valveTargetPercent",
                        AttributeValue.builder().n(Double.toString(valveTargetPercent)).build(),
                "expiresAt", AttributeValue.builder().n(Long.toString(expiresAt)).build());
    }

    private static String stringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value != null && value.s() != null ? value.s() : "";
    }

    private static double numberValue(Map<String, AttributeValue> item, String key) {
        return numberValue(item, key, 0);
    }

    private static double numberValue(Map<String, AttributeValue> item, String key, double defaultValue) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value.n());
    }

    private static long longValue(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.n() == null || value.n().isBlank()) {
            return 0;
        }
        return Long.parseLong(value.n());
    }
}
