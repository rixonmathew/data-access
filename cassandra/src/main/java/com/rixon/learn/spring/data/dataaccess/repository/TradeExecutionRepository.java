package com.rixon.learn.spring.data.dataaccess.repository;

import com.rixon.learn.spring.data.dataaccess.model.TradeExecutionKey;
import com.rixon.learn.spring.data.dataaccess.model.TradeExecutionRecord;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TradeExecutionRepository extends CassandraRepository<TradeExecutionRecord, TradeExecutionKey> {

    List<TradeExecutionRecord> findByKeyTickerAndKeyBucketDate(String ticker, LocalDate bucketDate);

    @Query("SELECT * FROM trade_executions WHERE ticker = ?0 AND bucket_date = ?1 AND execution_time >= ?2 AND execution_time <= ?3")
    List<TradeExecutionRecord> findTradesInTimeWindow(String ticker, LocalDate bucketDate, Instant startTime, Instant endTime);
}
