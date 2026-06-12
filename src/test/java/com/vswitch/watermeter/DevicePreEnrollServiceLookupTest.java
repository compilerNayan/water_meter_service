package com.vswitch.watermeter;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePreEnrollServiceLookupTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private UserService userService;
    @Mock private PreEnrollRepository preEnrollRepository;

    @Test
    void lookupTenantBySerialReturnsTenantId() {
        DevicePreEnrollService service =
                new DevicePreEnrollService(
                        dynamoDbClient, userService, preEnrollRepository, "WaterMeterDevicePreEnrollments");

        when(preEnrollRepository.findBySerialNumber("WM000123"))
                .thenReturn(
                        Optional.of(
                                new DevicePreEnrollRecord(
                                        "WM000123",
                                        "k3m9x2a",
                                        PreEnrollRepository.STATUS_PENDING,
                                        "2026-01-01T00:00:00Z",
                                        "2026-01-01T01:00:00Z",
                                        "user-1",
                                        null)));

        DeviceTenantLookupResponse response = service.lookupTenantBySerial("WM000123");

        assertEquals("WM000123", response.serialNumber());
        assertEquals("k3m9x2a", response.tenantId());
    }

    @Test
    void lookupTenantBySerialReturnsNotFoundWhenMissing() {
        DevicePreEnrollService service =
                new DevicePreEnrollService(
                        dynamoDbClient, userService, preEnrollRepository, "WaterMeterDevicePreEnrollments");

        when(preEnrollRepository.findBySerialNumber("WM999999")).thenReturn(Optional.empty());

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.lookupTenantBySerial("WM999999"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
