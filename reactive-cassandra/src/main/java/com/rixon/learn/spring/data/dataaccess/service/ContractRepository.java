package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractCassandra;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;

public interface ContractRepository extends ReactiveCassandraRepository<ContractCassandra, Integer> {
}
