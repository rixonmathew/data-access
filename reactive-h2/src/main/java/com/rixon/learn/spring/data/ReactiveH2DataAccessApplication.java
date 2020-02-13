package com.rixon.learn.spring.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
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
public class ReactiveH2DataAccessApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveH2DataAccessApplication.class);

    @Bean
    RouterFunction<ServerResponse> routerFunction(ReactiveContractRepository reactiveContractRepository) {
        return route(GET("/contracts"),
                request -> ok().body(reactiveContractRepository.findAll(), ContractRH2.class))
                .andRoute(GET("/contracts/{id}"),
                        request -> ok().body(reactiveContractRepository.findById(request.pathVariable("id")), ContractRH2.class))
                .andRoute(POST("/contracts").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<ContractRH2> body = serverRequest.body(BodyExtractors.toMono(ContractRH2.class));
                    reactiveContractRepository.saveAll(body);
                    return ok().body(reactiveContractRepository.saveAll(body),ContractRH2.class);
                });
    }


    @Bean
    CommandLineRunner commandLineRunner(ReactiveContractRepository contractRepository) {
        return args -> {
            contractRepository.deleteAll();
            long startTime=System.currentTimeMillis();
            contractRepository.saveAll(randomContracts(1000L))
                    .subscribe(null,null,()->{
                        LOGGER.info("Completed creating contracts");
                    });
            LOGGER.info("Created contracts in [{}] seconds",(System.currentTimeMillis()-startTime)/1000);
        };
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
