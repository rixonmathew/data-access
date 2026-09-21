package com.rixon.learn.spring.data.postgres.repository;

import com.rixon.model.order.Order;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OrderReactiveRepository extends R2dbcRepository<Order, Long> {

    Mono<Order> findByOrderId(String orderId);

    Flux<Order> findByAccountNumber(String accountNumber);

    Flux<Order> findByTicker(String ticker);
}
