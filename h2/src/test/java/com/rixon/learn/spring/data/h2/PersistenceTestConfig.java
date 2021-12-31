package com.rixon.learn.spring.data.h2;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
@EnableAutoConfiguration
@EntityScan({"com.rixon.model.contract"})
@ComponentScan(basePackages = "com.rixon.learn.spring.data.h2")
public class PersistenceTestConfig {
}
