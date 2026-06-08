package com.vswitch.watermeter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.ArrayList;
import java.util.List;
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

    public List<String> listAllTestIds() {
        var response =
                dynamoDbClient.scan(ScanRequest.builder().tableName(tableName).build());
        var testIds = new ArrayList<String>();
        for (var item : response.items()) {
            AttributeValue testId = item.get("test_id");
            if (testId != null && testId.s() != null) {
                testIds.add(testId.s());
            }
        }
        testIds.sort(String::compareTo);
        return testIds;
    }
}
