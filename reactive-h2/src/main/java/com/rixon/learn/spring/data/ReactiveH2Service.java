package com.rixon.learn.spring.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple3;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Random;
import java.util.stream.Stream;

@Service
public class ReactiveH2Service {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveH2Service.class);

    public final ReactiveContractRepository reactiveContractRepository;

    public ReactiveH2Service(ReactiveContractRepository reactiveContractRepository) {
        this.reactiveContractRepository = reactiveContractRepository;
    }

    public Flux<ContractEventRH2> events(ContractRH2 contractRH2) {
        LOGGER.info("Returning events for [{}]", contractRH2.getId());
        Flux<Long> duration = Flux.interval(Duration.ofMillis(10));
        Flux<Integer> interval = Flux.range(1, 2000);
        Flux<ContractEventRH2> eventFlux = Flux.fromStream(Stream.generate(() -> {
            int i = new Random().nextInt();
            return new ContractEventRH2("event-"+i, contractRH2.getId(), LocalDate.now(), randomEventType(), BigDecimal.TEN, i);
        }));
        return Flux.zip(duration,interval,eventFlux).map(Tuple3::getT3);
    }

    private String randomEventType() {
        String[] types = "INCEPTION,RE_RATE,REPRICE,RETURN".split(",");
        return types[new Random().nextInt(types.length)];

    }
}
