package com.rixon.learn.spring.data.mongodb.service;

import com.rixon.learn.spring.data.mongodb.document.OrderDocument;
import com.rixon.learn.spring.data.mongodb.dto.TickerOrderSummary;
import com.rixon.learn.spring.data.mongodb.repository.OrderDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderDocumentService {

    private final OrderDocumentRepository repository;
    private final MongoTemplate mongoTemplate;

    public OrderDocument save(OrderDocument order) {
        return repository.save(order);
    }

    public List<OrderDocument> saveAll(List<OrderDocument> orders) {
        return repository.saveAll(orders);
    }

    public Optional<OrderDocument> findByOrderId(String orderId) {
        return repository.findByOrderId(orderId);
    }

    public List<OrderDocument> findByStrategyType(String strategyType) {
        return repository.findByStrategyType(strategyType);
    }

    /**
     * Multi-stage aggregation pipeline: $match -> $group -> $project -> $sort
     */
    public List<TickerOrderSummary> calculateTickerSummaries(String side) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("side").is(side)),
                Aggregation.group("ticker")
                        .count().as("totalOrders")
                        .sum("quantity").as("totalVolume")
                        .avg("price").as("averagePrice"),
                Aggregation.project("totalOrders", "totalVolume", "averagePrice")
                        .and("_id").as("ticker"),
                Aggregation.sort(Sort.Direction.DESC, "totalVolume")
        );

        AggregationResults<TickerOrderSummary> results =
                mongoTemplate.aggregate(agg, "orders", TickerOrderSummary.class);
        return results.getMappedResults();
    }

    /**
     * Executes multi-faceted aggregation pipeline using $facet
     */
    public Document executeOrderDistributionFacet() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.facet(
                        Aggregation.group("status").count().as("count"),
                        Aggregation.project("count").and("_id").as("status")
                ).as("statusDistribution")
                .and(
                        Aggregation.group("orderType").count().as("count"),
                        Aggregation.project("count").and("_id").as("orderType")
                ).as("typeDistribution")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "orders", Document.class);
        return results.getUniqueMappedResult();
    }
}
