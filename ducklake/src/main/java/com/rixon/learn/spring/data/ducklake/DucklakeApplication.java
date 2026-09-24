package com.rixon.learn.spring.data.ducklake;

import com.rixon.learn.spring.data.ducklake.config.DuckLakeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DuckLakeProperties.class)
public class DucklakeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DucklakeApplication.class, args);
    }

}
