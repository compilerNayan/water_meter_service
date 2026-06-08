package com.vswitch.watermeter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class TenantService {

    private static final String DEFAULT_STRUCTURE = "{\"blocks\":[]}";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    TenantService(
            DynamoDbClient dynamoDbClient,
            @Value("${tenants.table.name:WaterMeterTenants}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    TenantRecord createTenant(String name, String ownerUserId) {
        String now = Instant.now().toString();
        String tenantId = "tenant_" + UUID.randomUUID().toString().replace("-", "");
        TenantRecord tenant =
                new TenantRecord(tenantId, name, ownerUserId, DEFAULT_STRUCTURE, now, now);
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(tenant.toItem())
                        .build());
        return tenant;
    }

    Optional<TenantRecord> findById(String tenantId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "tenantId",
                                                AttributeValue.builder().s(tenantId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TenantRecord.fromItem(response.item()));
    }
}
