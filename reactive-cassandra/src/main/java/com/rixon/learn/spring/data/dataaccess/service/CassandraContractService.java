package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractCassandra;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CassandraContractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraContractService.class);
    private final ContractRepository contractRepository;

    public CassandraContractService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public Flux<ContractCassandra> findAllContracts() {
        LOGGER.info("Returning all contracts");
        return contractRepository.findAll();
    }

    public Mono<ContractCassandra> byId(String id) {
        LOGGER.info("Returning contract for id [{}]", id);
        return contractRepository.findById(Integer.valueOf(id));
    }

    public Mono<String> createOrUpdate(Mono<ContractCassandra> contractMono) {
        return contractMono.flatMap(contract -> {
            if (contract != null) {
                return contractRepository.save(contract)
                        .map(saved -> "Created contract successfully with id " + saved.getId());
            } else {
                return Mono.just("Null contract received ");
            }
        });
    }
}
