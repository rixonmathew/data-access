package com.rixon.learn.spring.data.oracle19c.service;

import com.rixon.model.instrument.Instrument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
public class InstrumentService {

    @Autowired
    private InstrumentRepository instrumentRepository;

    public Flux<Instrument> findAll() {
        return Flux.fromIterable(instrumentRepository.findAll());
    }

    public Mono<Instrument> byId(String id){
        return Mono.just(instrumentRepository.findById(Long.valueOf(id)).get());
    }

    public Mono<String> createOrUpdate(Mono<Instrument> instrumentMono) {
        return null;
    }
}
