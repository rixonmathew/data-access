package com.rixon.learn.spring.data.spanner.config;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpannerConfig {

    @Value("${spanner.project-id:test-project}")
    private String projectId;

    @Value("${spanner.instance-id:test-instance}")
    private String instanceId;

    @Value("${spanner.database-id:capital-markets-db}")
    private String databaseId;

    @Value("${spanner.emulator-host:}")
    private String emulatorHost;

    @Bean
    public Spanner spanner() {
        SpannerOptions.Builder builder = SpannerOptions.newBuilder()
                .setProjectId(projectId);
        if (emulatorHost != null && !emulatorHost.isBlank()) {
            builder.setEmulatorHost(emulatorHost);
        }
        return builder.build().getService();
    }

    @Bean
    public DatabaseClient databaseClient(Spanner spanner) {
        DatabaseId dbId = DatabaseId.of(projectId, instanceId, databaseId);
        return spanner.getDatabaseClient(dbId);
    }
}
