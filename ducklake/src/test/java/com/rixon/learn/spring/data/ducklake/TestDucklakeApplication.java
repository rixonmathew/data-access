package com.rixon.learn.spring.data.ducklake;

import org.springframework.boot.SpringApplication;

public class TestDucklakeApplication {

    public static void main(String[] args) {
        SpringApplication.from(DucklakeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
