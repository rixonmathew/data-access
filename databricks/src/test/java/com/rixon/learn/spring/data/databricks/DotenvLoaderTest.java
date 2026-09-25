package com.rixon.learn.spring.data.databricks;

import com.rixon.learn.spring.data.databricks.config.DatabricksProperties;
import com.rixon.learn.spring.data.databricks.config.DotenvLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvLoaderTest {

    @Test
    @DisplayName("parseEnvFile parses tokens, exports, comments, and quotes correctly")
    void testParseEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        String content = """
                # Comment line
                DATABRICKS_TOKEN="dapi_sample_token_12345"
                export DATABRICKS_HOST='https://dbc-06bb552d-57db.cloud.databricks.com'
                DATABRICKS_ORG_ID=2102279257150258 # inline comment
                EMPTY_VAL=
                
                # Another comment
                DATABRICKS_CLUSTER_ID=0925-sample-cluster
                """;
        Files.writeString(envFile, content);

        Map<String, String> parsed = DotenvLoader.parseEnvFile(envFile);

        assertThat(parsed).containsEntry("DATABRICKS_TOKEN", "dapi_sample_token_12345");
        assertThat(parsed).containsEntry("DATABRICKS_HOST", "https://dbc-06bb552d-57db.cloud.databricks.com");
        assertThat(parsed).containsEntry("DATABRICKS_ORG_ID", "2102279257150258");
        assertThat(parsed).containsEntry("DATABRICKS_CLUSTER_ID", "0925-sample-cluster");
        assertThat(parsed).containsEntry("EMPTY_VAL", "");
        assertThat(parsed).doesNotContainKey("# Comment line");
    }

    @Test
    @DisplayName("DatabricksProperties uses default or configured values when .env is absent")
    void testDatabricksPropertiesDefaults() {
        DatabricksProperties properties = new DatabricksProperties();
        assertThat(properties.resolveHost()).isEqualTo("https://dbc-06bb552d-57db.cloud.databricks.com");
        assertThat(properties.resolveOrgId()).isEqualTo("2102279257150258");
    }
}
