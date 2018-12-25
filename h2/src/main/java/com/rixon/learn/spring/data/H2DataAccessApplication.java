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
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
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
                        request -> ok().body(contractService.byId(request.pathVariable("id")), Contract.class));
//                .andRoute(GET("/movies/{id}/events"),
//                        request ->ok()
//                                .contentType(MediaType.TEXT_EVENT_STREAM)
//                                .body(contractService.byId(request.pathVariable("id"))
//                                        .flatMapMany(contractService::events),MovieEvent.class));
    }


    @Bean
    CommandLineRunner commandLineRunner(ContractRepository contractRepository) {
        return args -> {
            contractRepository.deleteAll();
            contractRepository.saveAll(DataGeneratorUtils.randomContracts(1_000L));
            LOGGER.info("Created contracts");
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(H2DataAccessApplication.class, args);
    }

}
