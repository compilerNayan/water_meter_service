package com.vswitch.watermeter.device;

import java.util.Optional;

public final class MqttTopicParser {

    private static final String PREFIX = "vswitch/water/";

    private MqttTopicParser() {}

    public record ParsedMqttTopic(String tenantId, String deviceId, String suffix) {}

    public static Optional<ParsedMqttTopic> parse(String topic) {
        if (topic == null || topic.isBlank() || !topic.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String remainder = topic.substring(PREFIX.length());
        int firstSlash = remainder.indexOf('/');
        if (firstSlash <= 0) {
            return Optional.empty();
        }
        String tenantId = remainder.substring(0, firstSlash);
        String afterTenant = remainder.substring(firstSlash + 1);
        int secondSlash = afterTenant.indexOf('/');
        if (secondSlash <= 0) {
            return Optional.empty();
        }
        String deviceId = afterTenant.substring(0, secondSlash);
        String suffix = afterTenant.substring(secondSlash + 1);
        if (tenantId.isBlank() || deviceId.isBlank() || suffix.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedMqttTopic(tenantId, deviceId, suffix));
    }
}
