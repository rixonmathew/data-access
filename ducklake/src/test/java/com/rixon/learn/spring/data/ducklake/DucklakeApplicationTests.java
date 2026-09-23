package com.rixon.learn.spring.data.ducklake;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfDockerAvailable
class DucklakeApplicationTests {

    @Test
    void contextLoads() {
    }

}
