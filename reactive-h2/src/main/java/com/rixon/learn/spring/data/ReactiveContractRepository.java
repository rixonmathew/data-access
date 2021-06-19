package com.rixon.learn.spring.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.Collection;

public interface ReactiveContractRepository extends ReactiveCrudRepository<ContractRH2,String> {

    public Flux<ContractRH2> findContractRH2ByAssetIdentifierIn(Collection<String> ids);
}
