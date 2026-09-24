package com.rixon.learn.spring.data.arrow.config;

import com.rixon.learn.spring.data.arrow.flight.TradeFlightProducer;
import com.rixon.learn.spring.data.arrow.flightsql.DuckDbFlightSqlProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.arrow.flight.FlightProducer;
import org.apache.arrow.flight.FlightServer;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.auth2.CallHeaderAuthenticator;
import org.apache.arrow.memory.BufferAllocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Starts the Flight and Flight SQL gRPC servers. Each server depends on its producer, so on shutdown Spring
 * stops the server first, then the producer frees its buffers, then the root allocator is closed.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class FlightServerConfig {

    @Bean(destroyMethod = "close")
    public FlightServer tradeFlightServer(TradeFlightProducer producer, CallHeaderAuthenticator authenticator,
                                          ArrowProperties properties) throws IOException {
        return start("Flight", producer.allocator(), properties.getFlight(), producer, authenticator);
    }

    @Bean(destroyMethod = "close")
    public FlightServer flightSqlServer(DuckDbFlightSqlProducer producer, CallHeaderAuthenticator authenticator,
                                        ArrowProperties properties) throws IOException {
        return start("Flight SQL", producer.allocator(), properties.getFlightSql(), producer, authenticator);
    }

    private static FlightServer start(String name, BufferAllocator allocator,
                                      ArrowProperties.Server server, FlightProducer producer,
                                      CallHeaderAuthenticator authenticator) throws IOException {
        FlightServer flightServer = FlightServer.builder(allocator, Location.forGrpcInsecure(server.getHost(), server.getPort()), producer)
                .headerAuthenticator(authenticator)
                .build()
                .start();
        log.info("{} server listening on grpc://{}:{}", name, server.getHost(), flightServer.getPort());
        return flightServer;
    }
}
