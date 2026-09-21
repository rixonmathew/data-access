package com.rixon.learn.spring.data.dataaccess.repository;

import com.rixon.learn.spring.data.dataaccess.model.MarketTickKey;
import com.rixon.learn.spring.data.dataaccess.model.MarketTickRecord;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Repository
public interface MarketTickReactiveRepository extends ReactiveCassandraRepository<MarketTickRecord, MarketTickKey> {

    Flux<MarketTickRecord> findByKeyTickerAndKeyBucketHour(String ticker, String bucketHour);

    @Query("SELECT * FROM market_ticks WHERE ticker = ?0 AND bucket_hour = ?1 AND tick_timestamp >= ?2 AND tick_timestamp <= ?3")
    Flux<MarketTickRecord> findTicksInWindow(String ticker, String bucketHour, Instant start, Instant end);
}
