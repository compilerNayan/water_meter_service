package com.vswitch.watermeter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitServiceTest {

    @Mock private DynamoDbClient dynamoDbClient;
    @Mock private TenantMetadataService tenantMetadataService;

    private UnitService unitService;

    @BeforeEach
    void setUp() {
        unitService = new UnitService(dynamoDbClient, "WaterMeterUnits", tenantMetadataService);
    }

    @Test
    void createUnitReturnsPendingStatus() {
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        UnitResponse response =
                unitService.createUnit(
                        "k3m9x2a",
                        new CreateUnitRequest(
                                "WM000001",
                                "D205",
                                "D205",
                                "2",
                                "A",
                                "East",
                                "Ravi Kumar",
                                "+919876543210",
                                ""));

        assertEquals(UnitRecord.STATUS_PENDING, response.enrollmentStatus());

        ArgumentCaptor<PutItemRequest> putCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(putCaptor.capture());
        assertEquals(
                UnitRecord.STATUS_PENDING,
                putCaptor.getValue().item().get("enrollmentStatus").s());
        verify(tenantMetadataService).recomputeAndPersist("k3m9x2a");
    }

    @Test
    void getEnrollmentStatusReflectsDynamoDbRecord() {
        UnitRecord pending =
                new UnitRecord(
                        "wm-WM000001",
                        "k3m9x2a",
                        "WM000001",
                        "D205",
                        "D205",
                        "2",
                        "A",
                        "East",
                        "Resident",
                        "+1",
                        "",
                        UnitRecord.STATUS_PENDING,
                        "D205-1234",
                        "2026-01-01T00:00:00Z",
                        "2026-01-01T00:00:00Z");

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(
                        QueryResponse.builder()
                                .items(List.of(pending.toItem()))
                                .build());

        EnrollmentStatusResponse status =
                unitService.getEnrollmentStatus("k3m9x2a", "WM000001");

        assertFalse(status.enrolled());
        assertEquals(UnitRecord.STATUS_PENDING, status.status());

        UnitRecord enrolled =
                new UnitRecord(
                        pending.unitId(),
                        pending.tenantId(),
                        pending.deviceId(),
                        pending.name(),
                        pending.flatNumber(),
                        pending.floor(),
                        pending.block(),
                        pending.wing(),
                        pending.residentName(),
                        pending.phoneNumber(),
                        pending.notes(),
                        UnitRecord.STATUS_ENROLLED,
                        pending.unitInviteCode(),
                        pending.createdAt(),
                        pending.updatedAt());

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(
                        QueryResponse.builder()
                                .items(List.of(enrolled.toItem()))
                                .build());

        EnrollmentStatusResponse enrolledStatus =
                unitService.getEnrollmentStatus("k3m9x2a", "WM000001");

        assertTrue(enrolledStatus.enrolled());
        assertEquals(UnitRecord.STATUS_ENROLLED, enrolledStatus.status());
    }
}
