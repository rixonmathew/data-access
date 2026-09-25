package com.rixon.learn.spring.data.databricks.config;

import com.databricks.sdk.WorkspaceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rixon.learn.spring.data.databricks.service.DatabricksClusterManager;
import com.rixon.learn.spring.data.databricks.service.DatabricksRestClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DatabricksProperties.class)
public class DatabricksConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabricksConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public DatabricksRestClient databricksRestClient(DatabricksProperties properties, ObjectMapper objectMapper) {
        return DatabricksRestClient.create(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "databricks.token")
    public WorkspaceClient workspaceClient(DatabricksProperties properties) {
        if (!properties.hasToken()) {
            return null;
        }
        try {
            com.databricks.sdk.core.DatabricksConfig config = new com.databricks.sdk.core.DatabricksConfig()
                    .setHost(properties.resolveHost())
                    .setToken(properties.getToken());
            return new WorkspaceClient(config);
        } catch (Exception e) {
            LOGGER.warn("Could not initialize official Databricks SDK WorkspaceClient: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safety Hook: Automatically audits and cleans up active test clusters on application shutdown.
     */
    @Bean
    public AutoShutdownHook autoShutdownHook(DatabricksClusterManager clusterManager, DatabricksProperties properties) {
        return new AutoShutdownHook(clusterManager, properties);
    }

    public static class AutoShutdownHook {
        private final DatabricksClusterManager clusterManager;
        private final DatabricksProperties properties;

        public AutoShutdownHook(DatabricksClusterManager clusterManager, DatabricksProperties properties) {
            this.clusterManager = clusterManager;
            this.properties = properties;
        }

        @PreDestroy
        public void onExit() {
            if (properties.isAutoShutdownOnExit() && properties.hasToken()) {
                try {
                    LOGGER.info("Application shutting down: verifying no lingering Databricks compute resources...");
                    clusterManager.terminateAllRunningClusters();
                } catch (Exception e) {
                    LOGGER.debug("Auto-shutdown cluster cleanup skipped or failed: {}", e.getMessage());
                }
            }
        }
    }
}
