package com.rixon.learn.spring.data.arrow;

import com.rixon.learn.spring.data.arrow.config.ArrowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ArrowProperties.class)
public class ArrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArrowApplication.class, args);
    }

}
