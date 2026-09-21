package com.rixon.learn.spring.data.postgres.service;

import com.pgvector.PGvector;
import com.rixon.learn.spring.data.postgres.model.MarketResearchReport;
import com.rixon.learn.spring.data.postgres.model.OrderWithResearchInsight;
import com.rixon.learn.spring.data.postgres.model.VectorSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PgVectorResearchService {

    private final DatabaseClient databaseClient;

    public String formatVector(float[] vector) {
        return new PGvector(vector).toString();
    }

    public Mono<Void> insertReport(MarketResearchReport report) {
        String vectorStr = formatVector(report.getEmbedding());
        String sql = """
            INSERT INTO market_research_reports (id, ticker, title, summary, sector, sentiment, confidence_score, embedding)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8::vector)
            ON CONFLICT (id) DO UPDATE SET
                title = EXCLUDED.title,
                summary = EXCLUDED.summary,
                confidence_score = EXCLUDED.confidence_score,
                embedding = EXCLUDED.embedding
            """;

        return databaseClient.sql(sql)
                .bind("$1", report.getId())
                .bind("$2", report.getTicker())
                .bind("$3", report.getTitle())
                .bind("$4", report.getSummary())
                .bind("$5", report.getSector())
                .bind("$6", report.getSentiment())
                .bind("$7", report.getConfidenceScore())
                .bind("$8", vectorStr)
                .then();
    }

    public Flux<Void> insertReports(List<MarketResearchReport> reports) {
        return Flux.fromIterable(reports)
                .concatMap(this::insertReport);
    }

    public Mono<Long> countReports() {
        return databaseClient.sql("SELECT count(*) as total FROM market_research_reports")
                .map((row, metadata) -> row.get("total", Long.class))
                .one();
    }

    /**
     * Dense Vector Similarity Search using Cosine Distance (<=> operator).
     * Evaluated against pgvector HNSW index.
     */
    public Flux<VectorSearchResult> findSimilarByCosine(float[] queryVector, int limit) {
        String sql = """
            SELECT id, ticker, title, summary, sector, sentiment, confidence_score,
                   (embedding <=> $1::vector) AS distance,
                   1 - (embedding <=> $1::vector) AS similarity
            FROM market_research_reports
            ORDER BY embedding <=> $1::vector
            LIMIT $2
            """;

        return databaseClient.sql(sql)
                .bind("$1", formatVector(queryVector))
                .bind("$2", limit)
                .map((row, metadata) -> VectorSearchResult.builder()
                        .id(row.get("id", String.class))
                        .ticker(row.get("ticker", String.class))
                        .title(row.get("title", String.class))
                        .summary(row.get("summary", String.class))
                        .sector(row.get("sector", String.class))
                        .sentiment(row.get("sentiment", String.class))
                        .confidenceScore(row.get("confidence_score", Double.class) != null ? row.get("confidence_score", Double.class) : 0.0)
                        .distance(row.get("distance", Double.class) != null ? row.get("distance", Double.class) : 0.0)
                        .similarity(row.get("similarity", Double.class) != null ? row.get("similarity", Double.class) : 0.0)
                        .build())
                .all();
    }

    /**
     * Dense Vector Similarity Search using L2 / Euclidean Distance (<-> operator).
     */
    public Flux<VectorSearchResult> findSimilarByL2Distance(float[] queryVector, int limit) {
        String sql = """
            SELECT id, ticker, title, summary, sector, sentiment, confidence_score,
                   (embedding <-> $1::vector) AS distance,
                   1 / (1 + (embedding <-> $1::vector)) AS similarity
            FROM market_research_reports
            ORDER BY embedding <-> $1::vector
            LIMIT $2
            """;

        return databaseClient.sql(sql)
                .bind("$1", formatVector(queryVector))
                .bind("$2", limit)
                .map((row, metadata) -> VectorSearchResult.builder()
                        .id(row.get("id", String.class))
                        .ticker(row.get("ticker", String.class))
                        .title(row.get("title", String.class))
                        .summary(row.get("summary", String.class))
                        .sector(row.get("sector", String.class))
                        .sentiment(row.get("sentiment", String.class))
                        .confidenceScore(row.get("confidence_score", Double.class) != null ? row.get("confidence_score", Double.class) : 0.0)
                        .distance(row.get("distance", Double.class) != null ? row.get("distance", Double.class) : 0.0)
                        .similarity(row.get("similarity", Double.class) != null ? row.get("similarity", Double.class) : 0.0)
                        .build())
                .all();
    }

    /**
     * Hybrid Relational SQL Predicates + Vector Similarity Search in a single query.
     */
    public Flux<VectorSearchResult> findSimilarWithHybridFilter(float[] queryVector, String sector, String sentiment, int limit) {
        String sql = """
            SELECT id, ticker, title, summary, sector, sentiment, confidence_score,
                   (embedding <=> $1::vector) AS distance,
                   1 - (embedding <=> $1::vector) AS similarity
            FROM market_research_reports
            WHERE sector = $2 AND sentiment = $3
            ORDER BY embedding <=> $1::vector
            LIMIT $4
            """;

        return databaseClient.sql(sql)
                .bind("$1", formatVector(queryVector))
                .bind("$2", sector)
                .bind("$3", sentiment)
                .bind("$4", limit)
                .map((row, metadata) -> VectorSearchResult.builder()
                        .id(row.get("id", String.class))
                        .ticker(row.get("ticker", String.class))
                        .title(row.get("title", String.class))
                        .summary(row.get("summary", String.class))
                        .sector(row.get("sector", String.class))
                        .sentiment(row.get("sentiment", String.class))
                        .confidenceScore(row.get("confidence_score", Double.class) != null ? row.get("confidence_score", Double.class) : 0.0)
                        .distance(row.get("distance", Double.class) != null ? row.get("distance", Double.class) : 0.0)
                        .similarity(row.get("similarity", Double.class) != null ? row.get("similarity", Double.class) : 0.0)
                        .build())
                .all();
    }

    /**
     * Cross-Domain Relational JOIN between OLTP orders and pgvector research embeddings.
     */
    public Flux<OrderWithResearchInsight> correlateOrdersWithResearch(float[] themeVector, int limit) {
        String sql = """
            SELECT o.order_id, o.account_number, o.ticker, o.side, o.quantity, o.price,
                   r.title AS report_title, r.sentiment, r.confidence_score,
                   1 - (r.embedding <=> $1::vector) AS vector_similarity
            FROM orders o
            JOIN market_research_reports r ON o.ticker = r.ticker
            ORDER BY r.embedding <=> $1::vector
            LIMIT $2
            """;

        return databaseClient.sql(sql)
                .bind("$1", formatVector(themeVector))
                .bind("$2", limit)
                .map((row, metadata) -> OrderWithResearchInsight.builder()
                        .orderId(row.get("order_id", String.class))
                        .accountNumber(row.get("account_number", String.class))
                        .ticker(row.get("ticker", String.class))
                        .side(row.get("side", String.class))
                        .quantity(row.get("quantity", BigDecimal.class))
                        .price(row.get("price", BigDecimal.class))
                        .reportTitle(row.get("report_title", String.class))
                        .sentiment(row.get("sentiment", String.class))
                        .confidenceScore(row.get("confidence_score", Double.class) != null ? row.get("confidence_score", Double.class) : 0.0)
                        .vectorSimilarity(row.get("vector_similarity", Double.class) != null ? row.get("vector_similarity", Double.class) : 0.0)
                        .build())
                .all();
    }
}
