package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractCassandra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Service
public class CassandraContractService {

    private final static Logger LOGGER = LoggerFactory.getLogger(CassandraContractService.class);
    @Autowired
    private ContractRepository contractRepository;

    public Flux<ContractCassandra> findAllContracts() {
        LOGGER.info("Returning all contracts");
        return contractRepository.findAll();
    }

    public Mono<ContractCassandra> byId(String id) {
        LOGGER.info("Returning contract for id [{}]", id);
        return contractRepository.findById(Integer.valueOf(id));
    }

    public Mono<String> createOrUpdate(Mono<ContractCassandra> contractMono) {
        return contractMono.flatMap((Function<ContractCassandra, Mono<String>>) contract -> {
            if (contract != null) {
                Mono<ContractCassandra> save = contractRepository.save(contract);
                return save.flatMap((Function<ContractCassandra, Mono<String>>) contractCassandra ->
                        Mono.just("Created contract successfully with id " + contractCassandra.getId())
                );

            } else {
                return Mono.just("Null contract received ");
            }
        });
    }
}
