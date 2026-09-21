package com.rixon.learn.spring.data.qdrant.service;

import com.rixon.learn.spring.data.qdrant.dto.ResearchReportPoint;
import com.rixon.learn.spring.data.qdrant.dto.SearchResultDto;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RecommendPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.UpdateResult;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class QdrantVectorService {

    private final QdrantClient client;

    public void initCollection(String collectionName, long vectorSize) throws ExecutionException, InterruptedException {
        VectorParams vectorParams = VectorParams.newBuilder()
                .setSize(vectorSize)
                .setDistance(Distance.Cosine)
                .build();
        client.createCollectionAsync(collectionName, vectorParams).get();
        log.info("Initialized Qdrant collection: {} with dim: {}", collectionName, vectorSize);
    }

    public UpdateResult upsertReports(String collectionName, List<ResearchReportPoint> reports)
            throws ExecutionException, InterruptedException {
        List<PointStruct> points = new ArrayList<>();

        for (ResearchReportPoint report : reports) {
            Map<String, Value> payload = new HashMap<>();
            payload.put("ticker", ValueFactory.value(report.getTicker()));
            payload.put("title", ValueFactory.value(report.getTitle()));
            payload.put("summary", ValueFactory.value(report.getSummary()));
            payload.put("sector", ValueFactory.value(report.getSector()));
            payload.put("sentiment", ValueFactory.value(report.getSentiment()));
            payload.put("confidenceScore", ValueFactory.value(report.getConfidenceScore()));

            float[] vectorArr = new float[report.getVector().size()];
            for (int i = 0; i < report.getVector().size(); i++) {
                vectorArr[i] = report.getVector().get(i);
            }

            PointStruct point = PointStruct.newBuilder()
                    .setId(PointIdFactory.id(report.getId()))
                    .setVectors(VectorsFactory.vectors(vectorArr))
                    .putAllPayload(payload)
                    .build();

            points.add(point);
        }

        UpdateResult result = client.upsertAsync(collectionName, points).get();
        log.info("Upserted {} points into Qdrant collection {}", points.size(), collectionName);
        return result;
    }

    public List<SearchResultDto> searchSimilar(String collectionName, List<Float> queryVector, int limit)
            throws ExecutionException, InterruptedException {
        float[] vectorArr = new float[queryVector.size()];
        for (int i = 0; i < queryVector.size(); i++) {
            vectorArr[i] = queryVector.get(i);
        }

        SearchPoints searchPoints = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(toFloatList(vectorArr))
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        List<ScoredPoint> results = client.searchAsync(searchPoints).get();
        return mapToDto(results);
    }

    public List<SearchResultDto> searchWithFilter(
            String collectionName,
            List<Float> queryVector,
            String sector,
            String sentiment,
            int limit) throws ExecutionException, InterruptedException {

        float[] vectorArr = new float[queryVector.size()];
        for (int i = 0; i < queryVector.size(); i++) {
            vectorArr[i] = queryVector.get(i);
        }

        Filter.Builder filterBuilder = Filter.newBuilder();
        if (sector != null && !sector.isBlank()) {
            filterBuilder.addMust(ConditionFactory.matchKeyword("sector", sector));
        }
        if (sentiment != null && !sentiment.isBlank()) {
            filterBuilder.addMust(ConditionFactory.matchKeyword("sentiment", sentiment));
        }

        SearchPoints searchPoints = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(toFloatList(vectorArr))
                .setFilter(filterBuilder.build())
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        List<ScoredPoint> results = client.searchAsync(searchPoints).get();
        return mapToDto(results);
    }

    public List<SearchResultDto> recommendSimilar(
            String collectionName,
            List<Long> positiveIds,
            List<Long> negativeIds,
            int limit) throws ExecutionException, InterruptedException {

        RecommendPoints.Builder builder = RecommendPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());

        if (positiveIds != null) {
            for (Long id : positiveIds) {
                builder.addPositive(PointIdFactory.id(id));
            }
        }
        if (negativeIds != null) {
            for (Long id : negativeIds) {
                builder.addNegative(PointIdFactory.id(id));
            }
        }

        List<ScoredPoint> results = client.recommendAsync(builder.build()).get();
        return mapToDto(results);
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }

    private List<SearchResultDto> mapToDto(List<ScoredPoint> points) {
        List<SearchResultDto> dtos = new ArrayList<>();
        for (ScoredPoint pt : points) {
            PointId id = pt.getId();
            long idVal = id.hasNum() ? id.getNum() : 0L;

            Map<String, Value> payload = pt.getPayloadMap();
            String ticker = payload.containsKey("ticker") ? payload.get("ticker").getStringValue() : null;
            String title = payload.containsKey("title") ? payload.get("title").getStringValue() : null;
            String sector = payload.containsKey("sector") ? payload.get("sector").getStringValue() : null;
            String sentiment = payload.containsKey("sentiment") ? payload.get("sentiment").getStringValue() : null;

            dtos.add(SearchResultDto.builder()
                    .id(idVal)
                    .score(pt.getScore())
                    .ticker(ticker)
                    .title(title)
                    .sector(sector)
                    .sentiment(sentiment)
                    .build());
        }
        return dtos;
    }
}
