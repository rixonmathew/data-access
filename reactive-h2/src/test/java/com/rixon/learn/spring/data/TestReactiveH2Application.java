package com.rixon.learn.spring.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ReactiveH2DataAccessApplication.class,webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestReactiveH2Application {

    @Test
    @DisplayName("Test loading of context")
    public void contextLoads() {

    }
}
