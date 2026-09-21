package com.rixon.learn.spring.data.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationBuilders;
import com.rixon.learn.spring.data.elasticsearch.document.InstrumentDocument;
import com.rixon.learn.spring.data.elasticsearch.repository.InstrumentElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentSearchService {

    private final InstrumentElasticsearchRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    public void saveAll(Iterable<InstrumentDocument> documents) {
        repository.saveAll(documents);
    }

    public void deleteAll() {
        repository.deleteAll();
    }

    /**
     * BM25 relevance multi-field search boosting ticker (3x) and name (2x) over description.
     */
    public List<InstrumentDocument> searchByMultiField(String term) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(term)
                        .fields("ticker^3", "name^2", "description")
                ))
                .build();

        SearchHits<InstrumentDocument> hits = elasticsearchOperations.search(query, InstrumentDocument.class);
        return hits.stream().map(SearchHit::getContent).toList();
    }

    /**
     * Typo-tolerant fuzzy matching across company names (e.g., 'Nvidia' vs 'Nvdia').
     */
    public List<InstrumentDocument> searchByFuzzyName(String typoName) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.fuzzy(f -> f
                        .field("name")
                        .value(typoName)
                        .fuzziness("AUTO")
                ))
                .build();

        SearchHits<InstrumentDocument> hits = elasticsearchOperations.search(query, InstrumentDocument.class);
        return hits.stream().map(SearchHit::getContent).toList();
    }

    /**
     * Faceted sector distribution aggregation using native Elasticsearch terms aggregation.
     */
    public Map<String, Long> aggregateSectorCounts() {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withAggregation("sectors", AggregationBuilders.terms(t -> t.field("sector").size(10)))
                .withMaxResults(0)
                .build();

        SearchHits<InstrumentDocument> hits = elasticsearchOperations.search(query, InstrumentDocument.class);
        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) hits.getAggregations();
        if (aggregations == null) {
            return Map.of();
        }

        ElasticsearchAggregation agg = aggregations.get("sectors");
        if (agg == null) {
            return Map.of();
        }

        Aggregate aggregate = agg.aggregation().getAggregate();
        Map<String, Long> counts = new LinkedHashMap<>();
        aggregate.sterms().buckets().array().forEach(bucket ->
                counts.put(bucket.key().stringValue(), bucket.docCount())
        );
        return counts;
    }
}
