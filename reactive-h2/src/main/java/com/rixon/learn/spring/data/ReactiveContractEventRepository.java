package com.rixon.learn.spring.data;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactiveContractEventRepository  extends R2dbcRepository<ContractEventRH2,String> {
}
