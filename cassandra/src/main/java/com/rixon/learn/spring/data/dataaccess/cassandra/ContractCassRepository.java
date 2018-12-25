package com.rixon.learn.spring.data.dataaccess.cassandra;

import com.rixon.model.contract.Contract;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractCassRepository extends CassandraRepository<Contract,Long> {
}
