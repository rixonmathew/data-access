package com.rixon.learn.spring.data.databricks.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot EnvironmentPostProcessor that automatically loads properties from
 * databricks/.env or .env into the Spring Environment with high priority.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "databricksDotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> loaded = DotenvLoader.load();
        if (!loaded.isEmpty()) {
            Map<String, Object> props = new HashMap<>(loaded);
            // Also map lowercase spring property variants:
            // e.g. DATABRICKS_TOKEN -> databricks.token
            if (loaded.containsKey("DATABRICKS_TOKEN")) {
                props.putIfAbsent("databricks.token", loaded.get("DATABRICKS_TOKEN"));
            }
            if (loaded.containsKey("DATABRICKS_HOST")) {
                props.putIfAbsent("databricks.host", loaded.get("DATABRICKS_HOST"));
            }
            if (loaded.containsKey("DATABRICKS_ORG_ID")) {
                props.putIfAbsent("databricks.org-id", loaded.get("DATABRICKS_ORG_ID"));
            }
            if (loaded.containsKey("DATABRICKS_WORKSPACE_URL")) {
                props.putIfAbsent("databricks.workspace-url", loaded.get("DATABRICKS_WORKSPACE_URL"));
            }
            if (loaded.containsKey("DATABRICKS_CLUSTER_ID")) {
                props.putIfAbsent("databricks.cluster-id", loaded.get("DATABRICKS_CLUSTER_ID"));
            }
            if (loaded.containsKey("DATABRICKS_WAREHOUSE_ID")) {
                props.putIfAbsent("databricks.warehouse-id", loaded.get("DATABRICKS_WAREHOUSE_ID"));
            }

            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
