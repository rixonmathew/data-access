package com.rixon.learn.spring.data.redis.service;

import com.rixon.model.market.MarketQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisMarketQuoteStreamService {

    public static final String STREAM_KEY = "stream:market_quotes";
    private final StringRedisTemplate redisTemplate;

    public RecordId publishQuote(MarketQuote quote) {
        Map<String, String> fields = new HashMap<>();
        fields.put("ticker", quote.ticker());
        fields.put("timestamp", quote.timestamp().toString());
        fields.put("bidPrice", quote.bidPrice().toPlainString());
        fields.put("askPrice", quote.askPrice().toPlainString());
        fields.put("lastPrice", quote.lastPrice().toPlainString());
        fields.put("volume", String.valueOf(quote.volume()));

        MapRecord<String, String, String> record = StreamRecords.string(fields).withStreamKey(STREAM_KEY);
        return redisTemplate.opsForStream().add(record);
    }

    public List<MarketQuote> readLatestQuotes(int count) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .reverseRange(STREAM_KEY, Range.unbounded(), Limit.limit().count(count));

        if (records == null) {
            return List.of();
        }

        return records.stream().map(r -> {
            Map<Object, Object> value = r.getValue();
            return new MarketQuote(
                    (String) value.get("ticker"),
                    Instant.parse((String) value.get("timestamp")),
                    new BigDecimal((String) value.get("bidPrice")),
                    new BigDecimal((String) value.get("askPrice")),
                    new BigDecimal((String) value.get("lastPrice")),
                    Long.parseLong((String) value.get("volume"))
            );
        }).toList();
    }
}
