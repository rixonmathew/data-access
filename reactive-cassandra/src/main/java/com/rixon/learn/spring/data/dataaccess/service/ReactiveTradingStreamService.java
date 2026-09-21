package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.HotMarketQuote;
import com.rixon.learn.spring.data.dataaccess.model.MarketTickRecord;
import com.rixon.learn.spring.data.dataaccess.repository.HotMarketQuoteReactiveRepository;
import com.rixon.learn.spring.data.dataaccess.repository.MarketTickReactiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactiveTradingStreamService {

    private final MarketTickReactiveRepository tickRepository;
    private final HotMarketQuoteReactiveRepository quoteRepository;
    private final ReactiveCassandraTemplate reactiveCassandraTemplate;

    /**
     * Ingest a reactive stream of market ticks without blocking threads.
     */
    public Flux<MarketTickRecord> ingestTickStream(Flux<MarketTickRecord> tickStream) {
        return tickRepository.saveAll(tickStream);
    }

    /**
     * Stream ticks for a ticker and bucket hour (ordered newest first directly from clustering key).
     */
    public Flux<MarketTickRecord> streamTicksForHour(String ticker, String bucketHour) {
        return tickRepository.findByKeyTickerAndKeyBucketHour(ticker, bucketHour);
    }

    /**
     * Stream ticks within a specific time slice in a partition.
     */
    public Flux<MarketTickRecord> streamTicksInWindow(String ticker, String bucketHour, Instant start, Instant end) {
        return tickRepository.findTicksInWindow(ticker, bucketHour, start, end);
    }

    /**
     * Publish an ephemeral hot market quote with an explicit Time-To-Live (TTL).
     * Cassandra automatically purges/tombstones the record once the TTL expires.
     */
    public Mono<HotMarketQuote> publishHotQuoteWithTtl(HotMarketQuote quote, Duration ttl) {
        return reactiveCassandraTemplate.insert(
                quote,
                InsertOptions.builder().ttl(ttl).build()
        ).map(result -> quote);
    }

    /**
     * Fetch the current hot market quote. Returns empty Mono if expired by Cassandra TTL.
     */
    public Mono<HotMarketQuote> getHotQuote(String ticker) {
        return quoteRepository.findById(ticker);
    }
}
