package com.rixon.learn.spring.data.dataaccess.repository;

import com.rixon.learn.spring.data.dataaccess.model.OrderPaxosRecord;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderPaxosRepository extends CassandraRepository<OrderPaxosRecord, String> {
}
