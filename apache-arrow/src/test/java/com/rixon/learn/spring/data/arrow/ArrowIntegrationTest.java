package com.rixon.learn.spring.data.arrow;

import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shared test setup: both Flight servers on free ports and a smaller trades table. Every test class uses the
 * same properties, so they share one Spring context (one DuckDB, one pair of servers).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "arrow.flight.port=0",
        "arrow.flight-sql.port=0",
        "arrow.sample-rows=" + ArrowIntegrationTest.SAMPLE_ROWS
})
public @interface ArrowIntegrationTest {
    int SAMPLE_ROWS = 50_000;
}
