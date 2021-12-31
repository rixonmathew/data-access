package com.rixon.learn.spring.data.dataaccess;

import com.rixon.learn.spring.data.dataaccess.model.ContractEventMongo;
import com.rixon.learn.spring.data.dataaccess.model.ContractMongo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class DataAccessApplicationTests {

	private final static Logger LOGGER = LoggerFactory.getLogger(DataAccessApplicationTests.class);

	@Test
	@DisplayName("Context loads successfully!")
	public void contextLoads() {
	}

	@Test
	@DisplayName("Make calls to get contracts")
	public void testGetContracts() throws InterruptedException {
		WebClient webClient = WebClient.create("http://localhost:9696");
		WebClient.ResponseSpec contracts = webClient.method(HttpMethod.GET)
				.uri("/contracts")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve();

		contracts.onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.just(new RuntimeException(clientResponse.toString())));
		contracts.onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.just(new RuntimeException(clientResponse.toString())));
		Flux < ContractMongo > contractMongoFlux = contracts.bodyToFlux(ContractMongo.class);
		Disposable disposable = contractMongoFlux.subscribe(new Consumer<ContractMongo>() {
			@Override
			public void accept(ContractMongo contractMongo) {
				LOGGER.info("Got contract [{}]", contractMongo);
			}
		}, new Consumer<Throwable>() {
			@Override
			public void accept(Throwable throwable) {
				LOGGER.error("Got error ", throwable);
			}
		}, new Runnable() {
			@Override
			public void run() {
				LOGGER.info("Completed");
			}
		});
		while(!disposable.isDisposed()){}
	}


	@Test
	@DisplayName("Make parallel calls for streaming data")
	public void testGetEvents() throws InterruptedException {
		final Map<String,Integer> contractEventCount = new ConcurrentHashMap<>();
		WebClient webClient = WebClient.create("http://localhost:9696");
		WebClient.ResponseSpec contracts = webClient.method(HttpMethod.GET)
				.uri("/contracts")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve();

		contracts.onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.just(new RuntimeException(clientResponse.toString())));
		contracts.onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.just(new RuntimeException(clientResponse.toString())));
		Scheduler eventScheduler = Schedulers.fromExecutorService(Executors.newFixedThreadPool(8));
		List<Flux<ContractEventMongo>> eventFluxes = new ArrayList<>();
		Flux < ContractMongo > contractMongoFlux = contracts.bodyToFlux(ContractMongo.class);
		Disposable disposable = contractMongoFlux.subscribe(new Consumer<ContractMongo>() {
			@Override
			public void accept(ContractMongo contractMongo) {
//				LOGGER.info("Got contract [{}]", contractMongo);
				WebClient.ResponseSpec response = WebClient.create("http://localhost:9696")
						.method(HttpMethod.GET)
						.uri("/contracts/" + contractMongo.getId() + "/events")
						.accept(MediaType.APPLICATION_STREAM_JSON)
						.retrieve();

				eventFluxes.add(response.bodyToFlux(ContractEventMongo.class)
						.doOnEach(new Consumer<Signal<ContractEventMongo>>() {
							@Override
							public void accept(Signal<ContractEventMongo> contractEventMongoSignal) {
								ContractEventMongo contractEventMongo = contractEventMongoSignal.get();
								if (contractEventMongo!=null){
//									LOGGER.info("Got event [{}]",contractEventMongo);
									if  (contractEventCount.containsKey(contractEventMongo.getContractId())){
										int count = contractEventCount.get(contractEventMongo.getContractId());
										contractEventCount.put(contractEventMongo.getContractId(),++count);
									} else {
										contractEventCount.put(contractEventMongo.getContractId(),1);
									}
								}
							}
						}));
//						.subscribe(new Consumer<ContractEventMongo>() {
//							@Override
//							public void accept(ContractEventMongo contractEventMongo) {
//								//LOGGER.info("Got event [{}]",contractEventMongo);
//								if  (contractEventCount.containsKey(contractEventMongo.getContractId())){
//										int count = contractEventCount.get(contractEventMongo.getContractId());
//										contractEventCount.put(contractEventMongo.getContractId(),++count);
//								} else {
//									contractEventCount.put(contractEventMongo.getContractId(),1);
//								}
//							}
//						});
			}
		}, new Consumer<Throwable>() {
			@Override
			public void accept(Throwable throwable) {
				LOGGER.error("Got error ", throwable);
			}
		}, new Runnable() {
			@Override
			public void run() {
				LOGGER.info("Completed getting all contracts");
			}
		});
		while(!disposable.isDisposed()){}
		LOGGER.info("Waiting to accumulate events [{}]",eventFluxes.size());
//		Disposable eventDisposable = Flux.concat(eventFluxes)
//				.parallel()
//				.runOn(Schedulers.parallel())
//				.sequential()
//				.subscribe();
//		while(!eventDisposable.isDisposed()){}

		final List<Disposable> eventDisposables = new ArrayList<>();
		eventFluxes.forEach(eventFlux->{
			eventDisposables.add(eventFlux
					.parallel()
					.runOn(Schedulers.parallel())
					.sequential()
					.subscribe());
		});

		eventDisposables.forEach(disposable1 -> {
			while (!disposable1.isDisposed()) {}
		});

		LOGGER.info("Event count size [{}]",contractEventCount.size());
		contractEventCount.entrySet().forEach(entry->LOGGER.info("Contract [{}] Event count [{}]",entry.getKey(),entry.getValue()));
	}

}
