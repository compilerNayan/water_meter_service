package com.vswitch.watermeter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@Service
public class TestStoreService {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    TestStoreService(
            DynamoDbClient dynamoDbClient,
            @Value("${test.table.name:TestTable}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void storeTestId(String testId) {
        dynamoDbClient.putItem(
                PutItemRequest.builder()
                        .tableName(tableName)
                        .item(
                                Map.of(
                                        "test_id",
                                        AttributeValue.builder().s(testId).build()))
                        .build());
    }
}
