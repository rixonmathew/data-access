package com.rixon.learn.spring.data.mongodb.repository;

import com.rixon.learn.spring.data.mongodb.document.OrderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDocumentRepository extends MongoRepository<OrderDocument, String> {

    Optional<OrderDocument> findByOrderId(String orderId);

    List<OrderDocument> findByAccountNumber(String accountNumber);

    List<OrderDocument> findByTicker(String ticker);

    @Query("{ 'strategy.strategyType': ?0 }")
    List<OrderDocument> findByStrategyType(String strategyType);
}
