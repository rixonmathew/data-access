package com.rixon.learn.spring.data.service;

import com.rixon.learn.spring.data.h2.ContractRepository;
import com.rixon.model.contract.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Service
public class ContractService {

    private final static Logger LOGGER = LoggerFactory.getLogger(ContractService.class);
    @Autowired
    private ContractRepository contractRepository;

    public Flux<Contract> findAllContracts() {
        LOGGER.info("Returning all contracts");
        return Flux.fromIterable(contractRepository.findAll());
    }

    public Mono<Contract> byId(String id) {
        LOGGER.info("Returning contract for id [{}]", id);
        return Mono.just(contractRepository.findById(id).get());
    }

    public Mono<String> createOrUpdate(Mono<Contract> contractMono) {
        return contractMono.flatMap((Function<Contract, Mono<String>>) contract -> {
            if (contract != null) {
                contractRepository.save(contract);
                return Mono.just("Created contract successfully with id " + contract.getId());

            } else {
                return Mono.just("Null contract received ");
            }
        });
    }
}
