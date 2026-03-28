package com.rixon.learn.spring.data.oracle26ai.service;

import com.rixon.model.instrument.Instrument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
public class InstrumentService {

    private final InstrumentReactiveRepository instrumentRepository;

    @Autowired
    public InstrumentService(InstrumentReactiveRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    public Flux<Instrument> findAll() {
        return instrumentRepository.findAll();
    }

    public Mono<Instrument> byId(String id){
        return instrumentRepository.findById(Long.valueOf(id));
    }

    public Mono<Instrument> createOrUpdate(Mono<Instrument> instrumentMono) {
        return instrumentMono.flatMap(instrumentRepository::save);
    }

    public Mono<Void> deleteById(String id) {
        return instrumentRepository.deleteById(Long.valueOf(id));
    }

    public Mono<Instrument> randomInstrument() {
        //get random instrument from database
        return instrumentRepository.findAll()
                .take(1)
                .singleOrEmpty();
    }
}
