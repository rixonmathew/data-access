package com.rixon.ducklake.ducklake_poc;

import org.springframework.boot.SpringApplication;

public class TestDucklakePocApplication {

    public static void main(String[] args) {
        SpringApplication.from(DucklakePocApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
