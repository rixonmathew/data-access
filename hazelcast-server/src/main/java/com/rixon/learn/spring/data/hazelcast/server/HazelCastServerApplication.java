package com.rixon.learn.spring.data.hazelcast.server;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HazelCastServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HazelCastServerApplication.class,args);
    }

    @Bean
    public HazelcastInstance hazelcastInstance(){
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        return client;
    }
}