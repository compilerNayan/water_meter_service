package com.vswitch.watermeter.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTopicParserTest {

    @Test
    void parsesWaterMeterTopic() {
        var parsed =
                MqttTopicParser.parse(
                                "vswitch/water/k3m9x2a/WM000001/telemetry/second")
                        .orElseThrow();

        assertEquals("k3m9x2a", parsed.tenantId());
        assertEquals("WM000001", parsed.deviceId());
        assertEquals("telemetry/second", parsed.suffix());
    }

    @Test
    void parsesLifecycleEnrolledTopic() {
        var parsed =
                MqttTopicParser.parse(
                                "vswitch/water/tenant1/SERIAL123/lifecycle/enrolled")
                        .orElseThrow();

        assertEquals("lifecycle/enrolled", parsed.suffix());
    }

    @Test
    void rejectsInvalidTopic() {
        assertTrue(MqttTopicParser.parse("other/prefix/topic").isEmpty());
        assertTrue(MqttTopicParser.parse("vswitch/water/only-tenant").isEmpty());
    }
}
