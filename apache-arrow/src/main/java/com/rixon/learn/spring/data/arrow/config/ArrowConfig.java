package com.rixon.learn.spring.data.arrow.config;

import org.apache.arrow.flight.CallStatus;
import org.apache.arrow.flight.auth2.BasicCallHeaderAuthenticator;
import org.apache.arrow.flight.auth2.CallHeaderAuthenticator;
import org.apache.arrow.flight.auth2.GeneratedBearerTokenAuthenticator;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.memory.unsafe.UnsafeAllocationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ArrowConfig {

    /**
     * Root of all Arrow memory in the app. The allocation manager is set explicitly: the Netty one
     * is not on the classpath (see pom.xml), and choosing by system property or classpath order is fragile.
     * Closing it fails if any buffer is still allocated, so leaks surface at shutdown.
     */
    @Bean(destroyMethod = "close")
    public BufferAllocator rootAllocator() {
        return new RootAllocator(RootAllocator.configBuilder()
                .allocationManagerFactory(UnsafeAllocationManager.FACTORY)
                .build());
    }

    /**
     * Basic username/password on the first call, answered with a bearer token the client sends afterwards.
     * Shared by the Flight and Flight SQL servers.
     */
    @Bean
    public CallHeaderAuthenticator flightAuthenticator(ArrowProperties properties) {
        return new GeneratedBearerTokenAuthenticator(new BasicCallHeaderAuthenticator((username, password) -> {
            if (properties.getUsername().equals(username) && properties.getPassword().equals(password)) {
                return () -> username;
            }
            throw CallStatus.UNAUTHENTICATED.withDescription("Invalid username or password").toRuntimeException();
        }));
    }
}
