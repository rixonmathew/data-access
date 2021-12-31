package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractEventMongo;
import com.rixon.learn.spring.data.dataaccess.model.ContractMongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;


@Service
public class ReactiveMongoContractService {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReactiveMongoContractService.class);
    @Autowired
    private ReactiveMongoContractRepository contractRepository;

    public Flux<ContractMongo> findAllContracts() {
        LOGGER.info("Returning all contracts");
        return contractRepository.findAll();
    }

    public Mono<ContractMongo> byId(String id) {
        LOGGER.info("Returning contract for id [{}]", id);
        return contractRepository.findById(id);
    }

    public Mono<String> createOrUpdate(Mono<ContractMongo> contractMono) {
        return contractMono.flatMap((Function<ContractMongo, Mono<String>>) contract -> {
            if (contract != null) {
                Mono<ContractMongo> save = contractRepository.save(contract);
                return save.flatMap((Function<ContractMongo, Mono<String>>) contractCassandra ->
                        Mono.just("Created contract successfully with id " + contractCassandra.getId())
                );

            } else {
                return Mono.just("Null contract received ");
            }
        });
    }

    public  Flux<ContractEventMongo> events(ContractMongo contractMongo) {
        LOGGER.info("Returning events for [{}]",contractMongo.getId());
        Flux<Long> duration = Flux.interval(Duration.ofMillis(10));
        Flux<Integer> interval = Flux.range(1,2000);
        Flux<ContractEventMongo> contractEventMongoFlux = Flux.fromStream(Stream.generate((() -> {
            ContractEventMongo contractEventMongo = new ContractEventMongo();
            contractEventMongo.setId(UUID.randomUUID().toString());
            contractEventMongo.setContractId(contractMongo.getId());
            contractEventMongo.setDate(LocalDate.now());
            contractEventMongo.setDescription("This is a random event ");
            contractEventMongo.setType(randomEventType());
            LOGGER.info("Returning event with id [{}] for contract id [{}]",contractEventMongo.getId(),contractEventMongo.getContractId());
            return contractEventMongo;
        })));
        return Flux.zip(duration,interval,contractEventMongoFlux).map(Tuple3::getT3);
    }

    private String randomEventType() {
        String[] types = "INCEPTION,RERATE,REPRICE,RETURN".split(",");
        return types[new Random().nextInt(types.length)];

    }
}
