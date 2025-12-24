package com.rixon.learn.spring.data.oracle26ai.service;

import com.rixon.model.instrument.Instrument;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface InstrumentReactiveRepository extends R2dbcRepository<Instrument, Long> {
    @Query("SELECT count(*) FROM instrument")
    Mono<Long> getCount();
}
