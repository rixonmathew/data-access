package com.rixon.learn.spring.data.dataaccess.service;

import com.rixon.learn.spring.data.dataaccess.model.ContractEventMongo;
import com.rixon.learn.spring.data.dataaccess.model.ContractMongo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
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
        Flux<Long> interval = Flux.interval(Duration.ofMillis(100));
        Flux<ContractEventMongo> contractEventMongoFlux = Flux.fromStream(Stream.generate((new Supplier<ContractEventMongo>() {
            @Override
            public ContractEventMongo get() {
                ContractEventMongo contractEventMongo = new ContractEventMongo();
                contractEventMongo.setId(UUID.randomUUID().toString());
                contractEventMongo.setContractId(contractMongo.getId());
                contractEventMongo.setDate(LocalDate.now());
                contractEventMongo.setDescription("This is a random event ");
                contractEventMongo.setType(randomEventType());
                return contractEventMongo;
            }
        })));
        return Flux.zip(interval,contractEventMongoFlux).map(Tuple2::getT2);
    }

    private String randomEventType() {
        String[] types = "INCEPTION,RERATE,REPRICE,RETURN".split(",");
        return types[new Random().nextInt(types.length)];

    }
}
