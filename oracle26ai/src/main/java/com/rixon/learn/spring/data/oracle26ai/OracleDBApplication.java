package com.rixon.learn.spring.data.oracle26ai;

import com.rixon.learn.spring.data.oracle26ai.service.InstrumentReactiveRepository;
import com.rixon.learn.spring.data.oracle26ai.service.InstrumentService;
import com.rixon.model.instrument.Instrument;
import com.rixon.model.util.DataGeneratorUtils;
import oracle.r2dbc.OracleR2dbcOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EnableR2dbcRepositories
public class OracleDBApplication {

    static void main(String[] args) {
        SpringApplication.run(OracleDBApplication.class, args);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OracleDBApplication.class);

    @Value("${spring.r2dbc.properties.oracle.net.tns_admin}")
    private String tnsAdminPath;

    @Bean
    RouterFunction<ServerResponse> routerFunction(InstrumentService instrumentService) {
        return route(GET("/instruments"),
                request -> ok().body(instrumentService.findAll(), Instrument.class))
                .andRoute(GET("/instruments/stream"),
                        request -> ok().contentType(MediaType.TEXT_EVENT_STREAM).body(instrumentService.findAll(), Instrument.class))
                .andRoute(GET("/instruments/{id}"),
                        request -> ok().body(instrumentService.byId(request.pathVariable("id")), Instrument.class))
                .andRoute(POST("/instruments").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<Instrument> body = serverRequest.body(BodyExtractors.toMono(Instrument.class));
                    return ok().body(instrumentService.createOrUpdate(body),String.class);
                })
                .andRoute(DELETE("/instruments/{id}").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<Void> deleteResult = instrumentService.deleteById(serverRequest.pathVariable("id"));
                    return ok().body(deleteResult, String.class);
                })
                ;
    }

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer tnsAdminCustomizer() {
        return builder -> {
            // This ensures the TNS_ADMIN property is passed to the Oracle R2DBC driver
            builder.option(OracleR2dbcOptions.TNS_ADMIN, tnsAdminPath);
        };
    }

    @Bean
    CommandLineRunner commandLineRunner(InstrumentReactiveRepository instrumentRepository) {
        return _ -> {

            instrumentRepository.getCount().subscribe(count -> {
                LOGGER.info("Found {} instruments",count);
                if (count == 0) {
                    LOGGER.info("Creating instruments");
                    long startTime = System.currentTimeMillis();
                    List<Instrument> instruments = DataGeneratorUtils.randomInstruments(10_000);
                    LOGGER.info("Mocked instruments in [{}] ms",System.currentTimeMillis()-startTime);
                    instrumentRepository.saveAll(instruments).collectList().block();
                    LOGGER.info("Created instruments");
                }
            });
        };
    }
}

