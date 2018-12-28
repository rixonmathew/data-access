package com.rixon.learn.spring.data.oracle18c;

import com.rixon.learn.spring.data.oracle18c.service.InstrumentRepository;
import com.rixon.learn.spring.data.oracle18c.service.InstrumentService;
import com.rixon.model.instrument.Instrument;
import com.rixon.model.util.DataGeneratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EntityScan("com.rixon.model.instrument")
public class Oracle18cApplication {

    public static void main(String[] args) {
        SpringApplication.run(Oracle18cApplication.class, args);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Oracle18cApplication.class);

    @Bean
    RouterFunction<ServerResponse> routerFunction(InstrumentService instrumentService) {
        return route(GET("/instruments"),
                request -> ok().body(instrumentService.findAll(), Instrument.class))
                .andRoute(GET("/instruments/{id}"),
                        request -> ok().body(instrumentService.byId(request.pathVariable("id")), Instrument.class))
                .andRoute(POST("/instruments").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<Instrument> body = serverRequest.body(BodyExtractors.toMono(Instrument.class));
                    return ok().body(instrumentService.createOrUpdate(body),String.class);
                });
    }


    @Bean
    CommandLineRunner commandLineRunner(InstrumentRepository instrumentRepository) {
        return args -> {
            instrumentRepository.deleteAll();
            instrumentRepository.saveAll(DataGeneratorUtils.randomInstruments(100));
            LOGGER.info("Created instruments");
        };
    }


}

