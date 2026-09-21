package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.OrderPaxosRecord;
import com.rixon.learn.spring.data.dataaccess.model.TradeExecutionRecord;
import com.rixon.learn.spring.data.dataaccess.repository.OrderPaxosRepository;
import com.rixon.learn.spring.data.dataaccess.repository.TradeExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.EntityWriteResult;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.UpdateOptions;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CassandraTradingService {

    private final TradeExecutionRepository tradeExecutionRepository;
    private final OrderPaxosRepository orderPaxosRepository;
    private final CassandraTemplate cassandraTemplate;

    public TradeExecutionRecord saveExecution(TradeExecutionRecord execution) {
        return tradeExecutionRepository.save(execution);
    }

    public List<TradeExecutionRecord> saveAllExecutions(List<TradeExecutionRecord> executions) {
        return tradeExecutionRepository.saveAll(executions);
    }

    public List<TradeExecutionRecord> getExecutionsByBucket(String ticker, LocalDate bucketDate) {
        return tradeExecutionRepository.findByKeyTickerAndKeyBucketDate(ticker, bucketDate);
    }

    public List<TradeExecutionRecord> getExecutionsInWindow(String ticker, LocalDate bucketDate, Instant start, Instant end) {
        return tradeExecutionRepository.findTradesInTimeWindow(ticker, bucketDate, start, end);
    }

    /**
     * Submit order idempotently via Paxos (Lightweight Transaction).
     * Returns true if applied (inserted), false if orderId already exists.
     */
    public boolean submitOrderIfNotExists(OrderPaxosRecord order) {
        EntityWriteResult<OrderPaxosRecord> result = cassandraTemplate.insert(
                order,
                InsertOptions.builder().ifNotExists(true).build()
        );
        boolean applied = result.wasApplied();
        log.info("LWT submitOrderIfNotExists for order {}: applied={}", order.getOrderId(), applied);
        return applied;
    }

    /**
     * Transition order status conditionally via Paxos (Lightweight Transaction).
     * Only updates if current status matches expectedStatus.
     */
    public boolean transitionOrderStatus(String orderId, String expectedStatus, String newStatus, Long newVersion) {
        OrderPaxosRecord existing = cassandraTemplate.selectOneById(orderId, OrderPaxosRecord.class);
        if (existing == null) {
            log.warn("Order {} not found for Paxos status transition", orderId);
            return false;
        }

        existing.setStatus(newStatus);
        existing.setVersion(newVersion);

        EntityWriteResult<OrderPaxosRecord> result = cassandraTemplate.update(
                existing,
                UpdateOptions.builder().ifCondition(Criteria.where("status").is(expectedStatus)).build()
        );
        boolean applied = result.wasApplied();
        log.info("LWT transitionOrderStatus for order {} [{} -> {}]: applied={}",
                orderId, expectedStatus, newStatus, applied);
        return applied;
    }

    public Optional<OrderPaxosRecord> findOrderById(String orderId) {
        return orderPaxosRepository.findById(orderId);
    }
}
