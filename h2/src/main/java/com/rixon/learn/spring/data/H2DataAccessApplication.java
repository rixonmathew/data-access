package com.rixon.learn.spring.data;

import com.rixon.learn.spring.data.h2.ContractRepository;
import com.rixon.learn.spring.data.service.ContractService;
import com.rixon.model.contract.Contract;
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
@EntityScan(basePackages = {"com.rixon.model.contract"})
public class H2DataAccessApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2DataAccessApplication.class);

    @Bean
    RouterFunction<ServerResponse> routerFunction(ContractService contractService) {
        return route(GET("/contracts"),
                request -> ok().body(contractService.findAllContracts(), Contract.class))
                .andRoute(GET("/contracts/{id}"),
                        request -> ok().body(contractService.byId(request.pathVariable("id")), Contract.class))
                .andRoute(POST("/contracts").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<Contract> body = serverRequest.body(BodyExtractors.toMono(Contract.class));
                    return ok().body(contractService.createOrUpdate(body),String.class);
                });
    }


    @Bean
    CommandLineRunner commandLineRunner(ContractRepository contractRepository) {
        return args -> {
            contractRepository.deleteAll();
            long startTime=System.currentTimeMillis();
            contractRepository.saveAll(DataGeneratorUtils.randomContracts(1_000_000L));
            LOGGER.info("Created contracts in [{}] seconds",(System.currentTimeMillis()-startTime)/1000);
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(H2DataAccessApplication.class, args);
    }

}
