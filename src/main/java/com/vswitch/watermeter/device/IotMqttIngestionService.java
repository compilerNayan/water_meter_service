package com.vswitch.watermeter.device;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.watermeter.EnrollmentCompletionService;

@Service
public class IotMqttIngestionService {

    private static final Logger log = LoggerFactory.getLogger(IotMqttIngestionService.class);

    private final DeviceFacade deviceFacade;
    private final EnrollmentCompletionService enrollmentCompletionService;
    private final ObjectMapper objectMapper;

    IotMqttIngestionService(
            DeviceFacade deviceFacade,
            EnrollmentCompletionService enrollmentCompletionService,
            ObjectMapper objectMapper) {
        this.deviceFacade = deviceFacade;
        this.enrollmentCompletionService = enrollmentCompletionService;
        this.objectMapper = objectMapper;
    }

    public void handleEvent(Map<String, Object> event) {
        String topic = stringField(event, "mqttTopic", "topic");
        if (topic == null || topic.isBlank()) {
            log.warn("MQTT event missing topic field");
            return;
        }

        MqttTopicParser.ParsedMqttTopic parsed =
                MqttTopicParser.parse(topic)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unrecognized MQTT topic: " + topic));

        switch (parsed.suffix()) {
            case "lifecycle/enrolled" -> handleEnrolled(parsed, event);
            case "telemetry/second" -> handleSecondPulse(parsed, event);
            case "telemetry/bucket/30m" -> handleThirtyMinuteBucket(parsed, event);
            case "state/valve" -> handleValveState(parsed, event);
            default -> log.debug("Ignoring MQTT topic suffix {}", parsed.suffix());
        }
    }

    private void handleEnrolled(MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        String tenantId = firstNonBlank(stringField(event, "tenantId"), parsed.tenantId());
        String deviceId = firstNonBlank(stringField(event, "deviceId"), parsed.deviceId());
        String serialNumber = firstNonBlank(stringField(event, "serialNumber"), deviceId);
        if (!deviceId.equals(serialNumber)) {
            log.warn(
                    "Enrollment payload deviceId {} differs from serialNumber {}",
                    deviceId,
                    serialNumber);
        }
        String enrolledAt = stringField(event, "enrolledAt");
        enrollmentCompletionService.onEnrolled(tenantId, deviceId, enrolledAt);
    }

    private void handleSecondPulse(MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        String tenantId = parsed.tenantId();
        String deviceId = parsed.deviceId();
        String tsRaw = stringField(event, "ts");
        Instant ts =
                tsRaw != null && !tsRaw.isBlank() ? Instant.parse(tsRaw) : Instant.now();
        double ml = doubleField(event, "ml");
        deviceFacade.ingestSecondPulse(tenantId, deviceId, ts, ml);
    }

    private void handleThirtyMinuteBucket(
            MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            Map<String, Object> payload =
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

            String tenantId =
                    firstNonBlank(stringField(payload, "tenantId"), parsed.tenantId());
            String deviceId =
                    firstNonBlank(stringField(payload, "deviceId"), parsed.deviceId());
            String periodStartRaw = stringField(payload, "periodStart");
            Instant periodStart = Instant.parse(periodStartRaw);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> minuteMaps =
                    (List<Map<String, Object>>) payload.get("minutes");
            List<MinuteBucketEntry> minutes = new ArrayList<>();
            if (minuteMaps != null) {
                for (Map<String, Object> minute : minuteMaps) {
                    String t = stringField(minute, "t");
                    double ml = doubleField(minute, "ml");
                    minutes.add(new MinuteBucketEntry(Instant.parse(t), ml));
                }
            }

            double cumulativeLiters = doubleField(payload, "cumulativeLiters");
            double valveTargetPercent = doubleField(payload, "valveTargetPercent", 100.0);

            deviceFacade.ingest30MinuteBucket(
                    new ThirtyMinuteBucketPayload(
                            tenantId,
                            deviceId,
                            periodStart,
                            minutes,
                            cumulativeLiters,
                            valveTargetPercent));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid 30-minute bucket payload", e);
        }
    }

    private void handleValveState(MqttTopicParser.ParsedMqttTopic parsed, Map<String, Object> event) {
        String tenantId = parsed.tenantId();
        String deviceId = parsed.deviceId();
        double target =
                firstPresentDouble(
                        event,
                        "target",
                        "targetPressurePercent",
                        "valveTargetPercent");
        double actual =
                firstPresentDouble(
                        event,
                        "actual",
                        "actualPressurePercent",
                        "valveActualPercent");
        deviceFacade.ingestValveStateReport(tenantId, deviceId, target, actual);
    }

    private static String stringField(Map<String, Object> event, String... keys) {
        for (String key : keys) {
            Object value = event.get(key);
            if (value != null) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static double doubleField(Map<String, Object> event, String key) {
        return doubleField(event, key, 0.0);
    }

    private static double doubleField(Map<String, Object> event, String key, double defaultValue) {
        Object value = event.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static double firstPresentDouble(Map<String, Object> event, String... keys) {
        for (String key : keys) {
            if (event.containsKey(key) && event.get(key) != null) {
                return doubleField(event, key);
            }
        }
        return 0.0;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
