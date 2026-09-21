package com.rixon.learn.spring.data.elasticsearch.repository;

import com.rixon.learn.spring.data.elasticsearch.document.InstrumentDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstrumentElasticsearchRepository extends ElasticsearchRepository<InstrumentDocument, String> {

    List<InstrumentDocument> findBySector(String sector);

    List<InstrumentDocument> findByTicker(String ticker);
}
