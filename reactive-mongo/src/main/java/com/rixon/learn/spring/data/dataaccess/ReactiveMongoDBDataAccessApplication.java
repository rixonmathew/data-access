package com.rixon.learn.spring.data.dataaccess;

import com.rixon.learn.spring.data.dataaccess.model.ContractEventMongo;
import com.rixon.learn.spring.data.dataaccess.model.ContractMongo;
import com.rixon.learn.spring.data.dataaccess.service.ReactiveMongoContractRepository;
import com.rixon.learn.spring.data.dataaccess.service.ReactiveMongoContractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
@EntityScan("com.rixon.learn.spring.data.dataaccess.model")
public class ReactiveMongoDBDataAccessApplication {

	private final static Logger LOGGER = LoggerFactory.getLogger(ReactiveMongoDBDataAccessApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ReactiveMongoDBDataAccessApplication.class, args);
	}

	@Bean
	public RouterFunction<ServerResponse> routerFunction(ReactiveMongoContractService reactiveMongoContractService) {
		return route(GET("/contracts"),
				request -> ok().body(reactiveMongoContractService.findAllContracts(), ContractMongo.class))
				.andRoute(GET("/contracts/{id}"),
						request -> ok().body(reactiveMongoContractService.byId(request.pathVariable("id")),ContractMongo.class))
				.andRoute(GET("/contracts/{id}/events"),
						request ->ok()
								.contentType(MediaType.APPLICATION_OCTET_STREAM)
								.body(reactiveMongoContractService.byId(request.pathVariable("id"))
										.flatMapMany(reactiveMongoContractService::events), ContractEventMongo.class));

	}

	@Bean
	public CommandLineRunner commandLineRunner(ReactiveMongoContractRepository reactiveMongoContractRepository) {
		return args -> reactiveMongoContractRepository.deleteAll()
				.subscribe(null,null
						,()->{
							List<ContractMongo> contracts = IntStream.rangeClosed(1, 10_000)
									.mapToObj(ReactiveMongoDBDataAccessApplication::randomContract)
									.collect(Collectors.toList());
							reactiveMongoContractRepository.saveAll(contracts)
									.subscribe(null,null,()->{
										LOGGER.info("Completed creating contracts");
									});
						});
	}

	private static ContractMongo randomContract(int i) {
		ContractMongo contractMongo = new ContractMongo();
		contractMongo.setId(UUID.randomUUID().toString()+ i);
		contractMongo.setAssetIdentifier("C11234");
		contractMongo.setAssetIdentifierType("CUSIP");
		contractMongo.setComments("Test comments");
		contractMongo.setQuantity(new BigDecimal("1000"));
		contractMongo.setTradeDate(LocalDate.now());
		contractMongo.setSettlementDate(LocalDate.now().plusDays(3));
		contractMongo.setType("LOAN");
		return contractMongo;

	}
}
