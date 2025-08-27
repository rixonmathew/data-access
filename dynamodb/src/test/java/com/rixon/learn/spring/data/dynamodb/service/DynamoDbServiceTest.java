package com.rixon.learn.spring.data.dynamodb.service;

import com.rixon.learn.spring.data.dynamodb.config.TestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
@EnabledIfDockerAvailable
class DynamoDbServiceTest {

    @Autowired
    private DynamoDbService dynamoDbService;

    @Autowired
    private DynamoDbClient dynamoDbClient;

    private String tableName;

    @BeforeEach
    void setUp() {
        tableName = "test_table_" + UUID.randomUUID().toString().replace("-", "");
        dynamoDbService.createTable(tableName);
        waitForTableToBeActive(tableName);
    }

    @AfterEach
    void tearDown() {
        try {
            dynamoDbClient.deleteTable(DeleteTableRequest.builder()
                    .tableName(tableName)
                    .build());
        } catch (ResourceNotFoundException ignored) {
            // Table might not exist, which is fine
        }
    }

    private void waitForTableToBeActive(String tableName) {
        try {
            DescribeTableRequest request = DescribeTableRequest.builder()
                    .tableName(tableName)
                    .build();

            while (true) {
                DescribeTableResponse response = dynamoDbClient.describeTable(request);
                if (response.table().tableStatus() == TableStatus.ACTIVE) {
                    break;
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for table to be active", e);
        }
    }

    @Test
    void shouldCreateTableSuccessfully() {
        // Table creation is handled in setUp()
        // If we get here without exception, table was created successfully
        assertDoesNotThrow(() -> dynamoDbService.putItem(tableName, "test-id", "test-value"));
    }

    @Test
    void shouldPutAndGetItemSuccessfully() {
        // Given
        String id = "test-id";
        String value = "test-value";

        // When
        dynamoDbService.putItem(tableName, id, value);
        Map<String, AttributeValue> result = dynamoDbService.getItem(tableName, id);

        // Then
        assertNotNull(result);
        assertEquals(value, result.get("value").s());
        assertEquals(id, result.get("id").s());
    }

    @Test
    void shouldReturnNullForNonExistentItem() {
        // When
        Map<String, AttributeValue> result = dynamoDbService.getItem(tableName, "non-existent-id");

        // Then
        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void shouldThrowExceptionForNonExistentTable() {
        // When/Then
        assertThrows(ResourceNotFoundException.class,
                () -> dynamoDbService.getItem("non-existent-table", "test-id"));
    }
}
