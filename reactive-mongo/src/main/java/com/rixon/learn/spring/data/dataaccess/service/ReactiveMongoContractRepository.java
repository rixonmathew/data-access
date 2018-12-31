package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractMongo;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface ReactiveMongoContractRepository extends ReactiveCrudRepository<ContractMongo,String> {
}
