package com.rixon.ducklake.ducklake_poc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfDockerAvailable
class DucklakePocApplicationTests {

    @Test
    void contextLoads() {
    }

}
