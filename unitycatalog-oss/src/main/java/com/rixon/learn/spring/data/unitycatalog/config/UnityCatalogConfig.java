package com.rixon.learn.spring.data.unitycatalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UnityCatalogConfig {

    @Value("${unitycatalog.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder().baseUrl(baseUrl);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
