package com.rixon.learn.spring.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ReactiveH2Service {

    @Autowired
    public ReactiveContractRepository reactiveContractRepository;

    public Flux<ContractEventRH2> events(ContractRH2 contractRH2) {
        return null;
    }
}
