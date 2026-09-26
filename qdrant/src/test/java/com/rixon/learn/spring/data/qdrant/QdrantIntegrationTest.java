package com.rixon.learn.spring.data.qdrant;

import com.rixon.learn.spring.data.qdrant.dto.ResearchReportPoint;
import com.rixon.learn.spring.data.qdrant.dto.SearchResultDto;
import com.rixon.learn.spring.data.qdrant.service.QdrantVectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
class QdrantIntegrationTest {

    @Container
    static QdrantContainer qdrant = new QdrantContainer(DockerImageName.parse("qdrant/qdrant:latest"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("qdrant.host", qdrant::getHost);
        registry.add("qdrant.port", qdrant::getGrpcPort);
        registry.add("qdrant.use-tls", () -> false);
    }

    @Autowired
    private QdrantVectorService vectorService;

    private String collectionName;

    @BeforeEach
    void setUp() throws Exception {
        collectionName = "capital_markets_research_" + UUID.randomUUID().toString().replace("-", "");
        vectorService.initCollection(collectionName, 4);

        // Latent vector dimensions: [0: Semiconductor, 1: AI Datacenter, 2: Investment Banking, 3: Macro/Rates]
        List<ResearchReportPoint> reports = List.of(
                ResearchReportPoint.builder()
                        .id(1L)
                        .ticker("NVDA")
                        .title("AI GPU datacenter supercycle acceleration")
                        .summary("High margin enterprise inference driving hyperscaler capex")
                        .sector("SEMICONDUCTORS")
                        .sentiment("BULLISH")
                        .confidenceScore(0.96)
                        .vector(List.of(0.95f, 0.90f, 0.05f, 0.10f))
                        .build(),
                ResearchReportPoint.builder()
                        .id(2L)
                        .ticker("AMD")
                        .title("Next-gen server CPU & GPU market share gains")
                        .summary("Competitive alternative to single-vendor AI infrastructure")
                        .sector("SEMICONDUCTORS")
                        .sentiment("NEUTRAL")
                        .confidenceScore(0.82)
                        .vector(List.of(0.88f, 0.82f, 0.08f, 0.12f))
                        .build(),
                ResearchReportPoint.builder()
                        .id(3L)
                        .ticker("TSM")
                        .title("Advanced packaging CoWoS capacity ramp-up")
                        .summary("Foundry bottlenecks easing with next-gen fab deployment")
                        .sector("SEMICONDUCTORS")
                        .sentiment("BULLISH")
                        .confidenceScore(0.91)
                        .vector(List.of(0.92f, 0.75f, 0.05f, 0.20f))
                        .build(),
                ResearchReportPoint.builder()
                        .id(4L)
                        .ticker("JPM")
                        .title("Investment banking advisory and net interest margin resilience")
                        .summary("M&A pipeline revival offsetting loan loss provisions")
                        .sector("FINANCIALS")
                        .sentiment("BULLISH")
                        .confidenceScore(0.89)
                        .vector(List.of(0.05f, 0.10f, 0.92f, 0.85f))
                        .build(),
                ResearchReportPoint.builder()
                        .id(5L)
                        .ticker("GS")
                        .title("Global markets FICC trading and equity underwriting rebound")
                        .summary("Strong institutional client activity across fixed income and equities")
                        .sector("FINANCIALS")
                        .sentiment("BULLISH")
                        .confidenceScore(0.87)
                        .vector(List.of(0.08f, 0.15f, 0.95f, 0.80f))
                        .build()
        );

        vectorService.upsertReports(collectionName, reports);
    }

    @Test
    @DisplayName("Test 1: Dense vector similarity search (kNN) matches semantic AI chip reports")
    void testDenseVectorSimilaritySearch() throws Exception {
        // Query represents an AI semiconductor accelerator inquiry
        List<Float> queryVector = List.of(0.93f, 0.88f, 0.04f, 0.09f);

        List<SearchResultDto> results = vectorService.searchSimilar(collectionName, queryVector, 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTicker()).isEqualTo("NVDA");
        assertThat(results.get(0).getScore()).isGreaterThan(0.98f);
        assertThat(results.get(0).getSector()).isEqualTo("SEMICONDUCTORS");

        // The top 3 should all be semiconductors (NVDA, AMD, TSM)
        List<String> topTickers = results.stream().map(SearchResultDto::getTicker).toList();
        assertThat(topTickers).containsExactlyInAnyOrder("NVDA", "AMD", "TSM");
        assertThat(topTickers).doesNotContain("JPM", "GS");
    }

    @Test
    @DisplayName("Test 2: Hybrid search filters vector results by sector and sentiment payload")
    void testHybridVectorSearchWithFilter() throws Exception {
        // Query matches semiconductors
        List<Float> queryVector = List.of(0.90f, 0.85f, 0.05f, 0.10f);

        // Filter: Must be SEMICONDUCTORS and BULLISH
        // Note: AMD is NEUTRAL, so it should be excluded even though its vector similarity is high!
        List<SearchResultDto> results = vectorService.searchWithFilter(
                collectionName,
                queryVector,
                "SEMICONDUCTORS",
                "BULLISH",
                5
        );

        assertThat(results).hasSize(2);
        List<String> tickers = results.stream().map(SearchResultDto::getTicker).toList();
        assertThat(tickers).containsExactlyInAnyOrder("NVDA", "TSM");
        assertThat(tickers).doesNotContain("AMD"); // Filtered out by sentiment
    }

    @Test
    @DisplayName("Test 3: Recommendation engine finds points similar to positive anchor and distant from negative anchor")
    void testVectorRecommendationEngine() throws Exception {
        // Positive anchor: Point 1 (NVDA)
        // Negative anchor: Point 4 (JPM)
        List<SearchResultDto> recommendations = vectorService.recommendSimilar(
                collectionName,
                List.of(1L),
                List.of(4L),
                2
        );

        assertThat(recommendations).isNotEmpty();
        // Recommendations should belong to SEMICONDUCTORS (AMD or TSM), not FINANCIALS
        for (SearchResultDto rec : recommendations) {
            assertThat(rec.getSector()).isEqualTo("SEMICONDUCTORS");
            assertThat(rec.getTicker()).isNotEqualTo("JPM");
            assertThat(rec.getTicker()).isNotEqualTo("GS");
        }
    }
}
