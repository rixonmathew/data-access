package com.rixon.learn.spring.data;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.Collection;

@Repository
public interface ReactiveContractRepository extends R2dbcRepository<ContractRH2,String> {

    Flux<ContractRH2> findContractRH2ByAssetIdentifierIn(Collection<String> ids);
}
