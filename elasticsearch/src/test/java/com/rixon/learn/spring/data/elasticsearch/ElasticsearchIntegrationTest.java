package com.rixon.learn.spring.data.elasticsearch;

import com.rixon.learn.spring.data.elasticsearch.document.InstrumentDocument;
import com.rixon.learn.spring.data.elasticsearch.service.InstrumentSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class ElasticsearchIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.17.24")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private InstrumentSearchService searchService;

    @BeforeEach
    void setupData() {
        searchService.deleteAll();

        List<InstrumentDocument> batch = List.of(
                InstrumentDocument.builder()
                        .id("INST-NVDA")
                        .ticker("NVDA")
                        .name("NVIDIA Corporation")
                        .description("Designs graphics processing units GPUs and high performance artificial intelligence AI accelerators")
                        .assetClass("EQUITY")
                        .sector("SEMICONDUCTORS")
                        .marketCapBillions(2800.0)
                        .exchange("NASDAQ")
                        .lastTradedPrice(135.50)
                        .build(),
                InstrumentDocument.builder()
                        .id("INST-AAPL")
                        .ticker("AAPL")
                        .name("Apple Inc.")
                        .description("Consumer electronics manufacturer producing smartphones iPhone and laptops")
                        .assetClass("EQUITY")
                        .sector("CONSUMER_ELECTRONICS")
                        .marketCapBillions(3200.0)
                        .exchange("NASDAQ")
                        .lastTradedPrice(225.00)
                        .build(),
                InstrumentDocument.builder()
                        .id("INST-MSFT")
                        .ticker("MSFT")
                        .name("Microsoft Corporation")
                        .description("Provider of Azure cloud infrastructure, operating systems, and generative artificial intelligence AI copilots")
                        .assetClass("EQUITY")
                        .sector("SOFTWARE")
                        .marketCapBillions(3100.0)
                        .exchange("NASDAQ")
                        .lastTradedPrice(420.00)
                        .build(),
                InstrumentDocument.builder()
                        .id("INST-JPM")
                        .ticker("JPM")
                        .name("JPMorgan Chase & Co.")
                        .description("Global investment bank and financial services holding company")
                        .assetClass("EQUITY")
                        .sector("FINANCIALS")
                        .marketCapBillions(580.0)
                        .exchange("NYSE")
                        .lastTradedPrice(198.00)
                        .build(),
                InstrumentDocument.builder()
                        .id("INST-AMD")
                        .ticker("AMD")
                        .name("Advanced Micro Devices")
                        .description("Semiconductor manufacturer producing CPUs and Instinct artificial intelligence AI accelerators")
                        .assetClass("EQUITY")
                        .sector("SEMICONDUCTORS")
                        .marketCapBillions(250.0)
                        .exchange("NASDAQ")
                        .lastTradedPrice(155.00)
                        .build()
        );

        searchService.saveAll(batch);
    }

    @Test
    @DisplayName("Elasticsearch Scenario 1: Multi-Match BM25 Relevance Search with Field Boosting")
    void testBM25MultiMatchRelevanceSearch() {
        // Query for "artificial intelligence AI"
        List<InstrumentDocument> results = searchService.searchByMultiField("artificial intelligence AI");

        // NVDA, MSFT, AMD have "artificial intelligence AI" in description
        assertThat(results).hasSize(3);
        List<String> tickers = results.stream().map(InstrumentDocument::getTicker).toList();
        assertThat(tickers).contains("NVDA", "MSFT", "AMD");
        assertThat(tickers).doesNotContain("AAPL", "JPM");
    }

    @Test
    @DisplayName("Elasticsearch Scenario 2: Typo-Tolerant Fuzzy Search (Levenshtein Distance)")
    void testFuzzyTypoTolerance() {
        // Search with typo: "Microsft" instead of "Microsoft"
        List<InstrumentDocument> results = searchService.searchByFuzzyName("Microsft");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTicker()).isEqualTo("MSFT");
        assertThat(results.get(0).getName()).isEqualTo("Microsoft Corporation");
    }

    @Test
    @DisplayName("Elasticsearch Scenario 3: Real-Time Faceted Metric Aggregation by Sector")
    void testFacetedSectorAggregations() {
        Map<String, Long> sectorCounts = searchService.aggregateSectorCounts();

        // 2 in SEMICONDUCTORS (NVDA, AMD)
        // 1 in SOFTWARE (MSFT)
        // 1 in CONSUMER_ELECTRONICS (AAPL)
        // 1 in FINANCIALS (JPM)
        assertThat(sectorCounts).isNotEmpty();
        assertThat(sectorCounts.get("SEMICONDUCTORS")).isEqualTo(2L);
        assertThat(sectorCounts.get("SOFTWARE")).isEqualTo(1L);
        assertThat(sectorCounts.get("CONSUMER_ELECTRONICS")).isEqualTo(1L);
        assertThat(sectorCounts.get("FINANCIALS")).isEqualTo(1L);
    }
}
