package com.rixon.learn.spring.data.dataaccess.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeExecutionKey implements Serializable {

    @PrimaryKeyColumn(name = "ticker", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String ticker;

    @PrimaryKeyColumn(name = "bucket_date", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private LocalDate bucketDate;

    @PrimaryKeyColumn(name = "execution_time", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant executionTime;

    @PrimaryKeyColumn(name = "execution_id", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String executionId;
}
