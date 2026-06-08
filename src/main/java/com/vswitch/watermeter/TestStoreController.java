package com.vswitch.watermeter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TestStoreController {

    private final TestStoreService testStoreService;

    TestStoreController(TestStoreService testStoreService) {
        this.testStoreService = testStoreService;
    }

    /**
     * Stores {@code testId} as the partition key in DynamoDB TestTable.
     *
     * Example: PUT /test/my-value-123
     */
    @PutMapping("/test/{testId}")
    public ResponseEntity<Map<String, String>> storeTestId(@PathVariable String testId) {
        if (testId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "testId must not be blank"));
        }
        testStoreService.storeTestId(testId);
        return ResponseEntity.ok(Map.of("test_id", testId, "status", "stored"));
    }

    /** Returns all {@code test_id} values in TestTable as a JSON array. */
    @GetMapping("/test")
    public List<String> listAllTestIds() {
        return testStoreService.listAllTestIds();
    }
}
