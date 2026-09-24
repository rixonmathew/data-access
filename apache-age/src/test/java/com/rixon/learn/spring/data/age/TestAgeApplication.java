package com.rixon.learn.spring.data.age;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against the same AGE container the tests use. */
public class TestAgeApplication {

    public static void main(String[] args) {
        SpringApplication.from(AgeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
