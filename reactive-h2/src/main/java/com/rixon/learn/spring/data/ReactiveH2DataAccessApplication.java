package com.rixon.learn.spring.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EnableR2dbcRepositories
public class ReactiveH2DataAccessApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveH2DataAccessApplication.class);

    private final ReactiveH2Service reactiveH2Service;

    public ReactiveH2DataAccessApplication(ReactiveH2Service reactiveH2Service) {
        this.reactiveH2Service = reactiveH2Service;
    }


    @Bean
    RouterFunction<ServerResponse> routerFunction(ReactiveContractRepository reactiveContractRepository) {
        return route(GET("/contracts"),
                request -> ok().body(reactiveContractRepository.findAll(), ContractRH2.class))
                .andRoute(GET("/contracts/id/{id}"),
                        request -> ok().body(reactiveContractRepository.findById(request.pathVariable("id")), ContractRH2.class))
                .andRoute(GET("/contracts/assetidentifier/{assetIdentifier}"),
                        request -> ok().body(reactiveContractRepository.findContractRH2ByAssetIdentifierIn(List.of(request.pathVariable("id"))), ContractRH2.class))
                .andRoute(POST("/contracts").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<ContractRH2> body = serverRequest.body(BodyExtractors.toMono(ContractRH2.class));
                    return ok().body(reactiveContractRepository.saveAll(body),ContractRH2.class);
                })
				.andRoute(GET("/contracts/{id}/events"),
                request ->ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(reactiveContractRepository.findById(request.pathVariable("id"))
                                .flatMapMany(reactiveH2Service::events), ContractEventRH2.class));

    }




    @Bean
    CommandLineRunner commandLineRunner(ReactiveContractRepository contractRepository,
                                        ReactiveContractEventRepository reactiveContractEventRepository) {
        return args -> {
            LOGGER.info("Deleting all contracts");
            contractRepository.deleteAll().subscribe(null,null,()-> LOGGER.info("Completed deleting contracts"));
            long startTime=System.currentTimeMillis();
            LOGGER.info("Creating mock contracts");

            contractRepository.saveAll(randomContracts(10_000L))
                    .doOnComplete(()->LOGGER.info("Completed creating contracts in [{}]",System.currentTimeMillis()-startTime))
                    .doOnError(throwable -> LOGGER.info("Got error creating contracts [{}]",throwable.getLocalizedMessage()))
                    .subscribe(contractRH2 -> reactiveContractEventRepository.saveAll(randomEvents(contractRH2.getId()))
                            .doOnError(throwable -> LOGGER.info("Got error creating events [{}]",throwable.getLocalizedMessage()))
                            .subscribe());
        };
    }

    private Flux<ContractEventRH2> randomEvents(String contractId) {
        return Flux.fromStream(LongStream.range(0,10)
                .mapToObj(index->{
                    ContractEventRH2 contractEventRH2 = new ContractEventRH2();
                    contractEventRH2.setId(UUID.randomUUID().toString());
                    contractEventRH2.setContractId(contractId);
                    contractEventRH2.setType("REPRICE");
                    contractEventRH2.setEventDate(LocalDate.now());
                    contractEventRH2.setQuantity(10);
                    contractEventRH2.setEconomicChange(BigDecimal.TEN);
                    return contractEventRH2;
                }));
    }

    private List<ContractRH2> randomContracts(long count) {
        return LongStream.range(0,count)
                .mapToObj(index->{
                    ContractRH2 contract = new ContractRH2();
                    contract.setId(UUID.randomUUID().toString());
                    contract.setType("LOAN");
                    contract.setAssetIdentifierType("CUSIP");
                    contract.setAssetIdentifier("C1123323");
                    contract.setQuantity(BigDecimal.valueOf(100));
                    contract.setTradeDate(LocalDate.now());
                    contract.setSettlementDate(LocalDate.now().plusDays(3));
                    contract.setComments(String.valueOf(index));
                    return contract;
                })
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        SpringApplication.run(ReactiveH2DataAccessApplication.class, args);
    }

}
