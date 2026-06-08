package com.vswitch.watermeter;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Service
public class UserService {

    private final DynamoDbClient dynamoDbClient;
    private final TenantService tenantService;
    private final String tableName;

    UserService(
            DynamoDbClient dynamoDbClient,
            TenantService tenantService,
            @Value("${users.table.name:WaterMeterUsers}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tenantService = tenantService;
        this.tableName = tableName;
    }

    public Optional<UserRecord> findById(String userId) {
        var response =
                dynamoDbClient.getItem(
                        GetItemRequest.builder()
                                .tableName(tableName)
                                .key(
                                        Map.of(
                                                "userId",
                                                AttributeValue.builder().s(userId).build()))
                                .build());
        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UserRecord.fromItem(response.item()));
    }

    UserResponse getMe(String userId) {
        return findById(userId)
                .map(UserRecord::toResponse)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "User not registered"));
    }

    UserRegistrationResult registerUser(
            String userId, String tokenEmail, CreateUserRequest request) {
        validateRequest(request);

        var existing = findById(userId);
        if (existing.isPresent()) {
            return new UserRegistrationResult(existing.get().toResponse(), false);
        }

        if (tokenEmail != null
                && !tokenEmail.isBlank()
                && request.email() != null
                && !request.email().equalsIgnoreCase(tokenEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Email does not match authenticated user");
        }

        TenantRecord tenant = tenantService.createTenant(request.tenantName(), userId);
        String now = Instant.now().toString();
        String displayName = buildDisplayName(request.firstName(), request.lastName());

        UserRecord user =
                new UserRecord(
                        userId,
                        request.email(),
                        nullToEmpty(request.phone()),
                        request.firstName(),
                        request.lastName(),
                        displayName,
                        tenant.tenantId(),
                        true,
                        true,
                        now,
                        now);

        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(user.toItem())
                        .build());

        return new UserRegistrationResult(user.toResponse(), true);
    }

    record UserRegistrationResult(UserResponse response, boolean created) {}

    private void validateRequest(CreateUserRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body required");
        }
        if (isBlank(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (isBlank(request.firstName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "firstName is required");
        }
        if (isBlank(request.lastName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastName is required");
        }
        if (isBlank(request.tenantName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantName is required");
        }
    }

    private static String buildDisplayName(String firstName, String lastName) {
        return (firstName + " " + lastName).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
