package com.vswitch.watermeter.device;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vswitch.watermeter.EnrollmentCompletionService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IotMqttIngestionServiceTest {

    @Mock private DeviceFacade deviceFacade;
    @Mock private EnrollmentCompletionService enrollmentCompletionService;

    private IotMqttIngestionService service;

    @BeforeEach
    void setUp() {
        service =
                new IotMqttIngestionService(
                        deviceFacade, enrollmentCompletionService, new ObjectMapper());
    }

    @Test
    void routesLifecycleEnrolled() {
        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "vswitch/water/k3m9x2a/WM000001/lifecycle/enrolled",
                        "tenantId",
                        "k3m9x2a",
                        "deviceId",
                        "WM000001",
                        "serialNumber",
                        "WM000001",
                        "enrolledAt",
                        "2026-06-09T10:00:00Z"));

        verify(enrollmentCompletionService)
                .onEnrolled(eq("k3m9x2a"), eq("WM000001"), eq("2026-06-09T10:00:00Z"));
    }

    @Test
    void routesSecondPulse() {
        service.handleEvent(
                Map.of(
                        "mqttTopic",
                        "vswitch/water/k3m9x2a/WM000001/telemetry/second",
                        "ts",
                        "2026-06-09T10:30:05Z",
                        "ml",
                        45));

        verify(deviceFacade)
                .ingestSecondPulse(
                        eq("k3m9x2a"),
                        eq("WM000001"),
                        eq(Instant.parse("2026-06-09T10:30:05Z")),
                        eq(45.0));
    }
}
