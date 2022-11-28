package com.rixon.learn.spring.data.dataaccess;

import com.rixon.learn.spring.data.dataaccess.model.ContractCassandra;
import com.rixon.learn.spring.data.dataaccess.service.CassandraContractService;
import com.rixon.learn.spring.data.dataaccess.service.ContractRepository;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EntityScan(basePackages = {"com.rixon.learn.spring.data.dataaccess.model"})
public class ReactiveCassandraApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveCassandraApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ReactiveCassandraApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(CassandraContractService contractService) {
		return route(GET("/contracts"),
				request -> ok().body(contractService.findAllContracts(), ContractCassandra.class))
				.andRoute(GET("/contracts/{id}"),
						request -> ok().body(contractService.byId(request.pathVariable("id")), ContractCassandra.class))
				.andRoute(POST("/contracts").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
					Mono<ContractCassandra> body = serverRequest.body(BodyExtractors.toMono(ContractCassandra.class));
					return ok().body(contractService.createOrUpdate(body),String.class);
				});
	}

	@Bean
	CommandLineRunner commandLineRunner(ContractRepository contractRepository) {
		return args -> {
			contractRepository.deleteAll().doOnSuccess(aVoid -> LOGGER.info("Deleted all contracts")).subscribe();
			Flux<ContractCassandra> contractCassandraFlux = contractRepository.saveAll(randomContracts(10000)).subscribeOn(Schedulers.boundedElastic());
			contractCassandraFlux.doOnComplete(() -> LOGGER.info("Completed creating contracts")).subscribe();
		};
	}

	private Flux<ContractCassandra> randomContracts(int count) {
		return Flux.fromIterable(IntStream.rangeClosed(1,count).mapToObj(i->{
			ContractCassandra contractCassandra = new ContractCassandra();
			contractCassandra.setId(i);
			contractCassandra.setAccountId("Account ["+i+"]");
			contractCassandra.setAssetId("Asset ["+i+"]");
			return contractCassandra;
		}).collect(Collectors.toList()));
	}
}
