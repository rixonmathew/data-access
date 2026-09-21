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

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketTickKey implements Serializable {

    @PrimaryKeyColumn(name = "ticker", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String ticker;

    @PrimaryKeyColumn(name = "bucket_hour", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String bucketHour;

    @PrimaryKeyColumn(name = "tick_timestamp", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant tickTimestamp;

    @PrimaryKeyColumn(name = "tick_id", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String tickId;
}
