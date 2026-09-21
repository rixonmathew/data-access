package com.rixon.learn.spring.data.dataaccess.repository;

import com.rixon.learn.spring.data.dataaccess.model.HotMarketQuote;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HotMarketQuoteReactiveRepository extends ReactiveCassandraRepository<HotMarketQuote, String> {
}
