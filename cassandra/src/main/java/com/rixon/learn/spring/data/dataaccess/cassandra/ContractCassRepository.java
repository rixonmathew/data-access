package com.rixon.learn.spring.data.dataaccess.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;

public interface ContractCassRepository extends CassandraRepository<ContractCassandraNonReactive,String> {

}
