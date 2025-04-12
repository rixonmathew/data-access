package com.rixon.learn.spring.data.dynamodb.service;

import com.rixon.learn.spring.data.dynamodb.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestConfig.class)
class DynamoDbServiceTest {

    @Autowired
    private DynamoDbService dynamoDbService;

    private String tableName;

    @BeforeEach
    void setUp() {
        tableName = "test_table_" + UUID.randomUUID().toString().replace("-", "");
        dynamoDbService.createTable(tableName);
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
