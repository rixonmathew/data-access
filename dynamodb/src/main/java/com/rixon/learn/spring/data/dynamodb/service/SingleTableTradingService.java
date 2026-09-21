package com.rixon.learn.spring.data.dynamodb.service;

import com.rixon.learn.spring.data.dynamodb.model.OrderAggregateDto;
import com.rixon.learn.spring.data.dynamodb.model.SingleTableExecutionEntity;
import com.rixon.learn.spring.data.dynamodb.model.SingleTableOrderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SingleTableTradingService {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;

    private final TableSchema<SingleTableOrderEntity> orderSchema = TableSchema.fromBean(SingleTableOrderEntity.class);
    private final TableSchema<SingleTableExecutionEntity> execSchema = TableSchema.fromBean(SingleTableExecutionEntity.class);

    public void createSingleTable(String tableName) {
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(tableName)
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("gsi1pk").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("gsi1sk").attributeType(ScalarAttributeType.S).build()
                )
                .keySchema(
                        KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
                )
                .globalSecondaryIndexes(
                        GlobalSecondaryIndex.builder()
                                .indexName("gsi1")
                                .keySchema(
                                        KeySchemaElement.builder().attributeName("gsi1pk").keyType(KeyType.HASH).build(),
                                        KeySchemaElement.builder().attributeName("gsi1sk").keyType(KeyType.RANGE).build()
                                )
                                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                                .build()
                )
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build();

        dynamoDbClient.createTable(request);
        log.info("Created Single-Table: {} with GSI1", tableName);
    }

    public SingleTableOrderEntity saveOrder(String tableName, SingleTableOrderEntity order) {
        order.setPk("ORDER#" + order.getOrderId());
        order.setSk("METADATA");
        order.setGsi1pk("ACCOUNT#" + order.getAccountId());
        order.setGsi1sk("ORDER#" + order.getCreatedAt());
        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(0L);
        }

        DynamoDbTable<SingleTableOrderEntity> table = enhancedClient.table(tableName, orderSchema);
        table.putItem(order);
        log.info("Saved order {} for account {}", order.getOrderId(), order.getAccountId());
        return order;
    }

    public SingleTableOrderEntity updateOrderWithOptimisticLocking(String tableName, SingleTableOrderEntity order) {
        DynamoDbTable<SingleTableOrderEntity> table = enhancedClient.table(tableName, orderSchema);
        return table.updateItem(order);
    }

    public SingleTableOrderEntity getOrder(String tableName, String orderId) {
        DynamoDbTable<SingleTableOrderEntity> table = enhancedClient.table(tableName, orderSchema);
        return table.getItem(Key.builder().partitionValue("ORDER#" + orderId).sortValue("METADATA").build());
    }

    public void recordExecutionAtomic(String tableName, SingleTableExecutionEntity execution) {
        DynamoDbTable<SingleTableOrderEntity> orderTable = enhancedClient.table(tableName, orderSchema);
        DynamoDbTable<SingleTableExecutionEntity> execTable = enhancedClient.table(tableName, execSchema);

        SingleTableOrderEntity order = getOrder(tableName, execution.getOrderId());
        if (order == null) {
            throw new IllegalArgumentException("Cannot execute against non-existent order: " + execution.getOrderId());
        }

        long newFilledQty = (order.getFilledQuantity() != null ? order.getFilledQuantity() : 0L) + execution.getExecutedQuantity();
        order.setFilledQuantity(newFilledQty);
        if (newFilledQty >= order.getQuantity()) {
            order.setStatus("FILLED");
        } else {
            order.setStatus("PARTIALLY_FILLED");
        }

        execution.setPk("ORDER#" + execution.getOrderId());
        execution.setSk("EXECUTION#" + execution.getExecutionId());
        execution.setGsi1pk("TICKER#" + execution.getTicker());
        execution.setGsi1sk("EXECUTION#" + execution.getExecutedAt());

        TransactWriteItemsEnhancedRequest txRequest = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(orderTable, order)
                .addPutItem(execTable, execution)
                .build();

        enhancedClient.transactWriteItems(txRequest);
        log.info("Atomically recorded execution {} for order {}, new filled qty: {}",
                execution.getExecutionId(), order.getOrderId(), newFilledQty);
    }

    public OrderAggregateDto getOrderAggregate(String tableName, String orderId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(":pk", AttributeValue.builder().s("ORDER#" + orderId).build()))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        SingleTableOrderEntity order = null;
        List<SingleTableExecutionEntity> executions = new ArrayList<>();

        for (Map<String, AttributeValue> item : response.items()) {
            String sk = item.get("sk").s();
            if ("METADATA".equals(sk)) {
                order = orderSchema.mapToItem(item);
            } else if (sk.startsWith("EXECUTION#")) {
                executions.add(execSchema.mapToItem(item));
            }
        }

        return OrderAggregateDto.builder()
                .order(order)
                .executions(executions)
                .build();
    }

    public List<SingleTableOrderEntity> findOrdersByAccount(String tableName, String accountId) {
        DynamoDbTable<SingleTableOrderEntity> orderTable = enhancedClient.table(tableName, orderSchema);
        DynamoDbIndex<SingleTableOrderEntity> index = orderTable.index("gsi1");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue("ACCOUNT#" + accountId)
                .build());

        List<SingleTableOrderEntity> orders = new ArrayList<>();
        index.query(r -> r.queryConditional(queryConditional))
                .stream()
                .forEach(page -> orders.addAll(page.items()));

        return orders;
    }

    public List<SingleTableExecutionEntity> findExecutionsByTicker(String tableName, String ticker) {
        DynamoDbTable<SingleTableExecutionEntity> execTable = enhancedClient.table(tableName, execSchema);
        DynamoDbIndex<SingleTableExecutionEntity> index = execTable.index("gsi1");

        QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue("TICKER#" + ticker)
                .build());

        List<SingleTableExecutionEntity> list = new ArrayList<>();
        index.query(r -> r.queryConditional(queryConditional))
                .stream()
                .forEach(page -> list.addAll(page.items()));

        return list;
    }
}
