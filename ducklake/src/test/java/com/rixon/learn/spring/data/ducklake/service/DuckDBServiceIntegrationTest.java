package com.rixon.learn.spring.data.ducklake.service;

import com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.rixon.learn.spring.data.ducklake.TestcontainersConfiguration.DATA_BUCKET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads CSV from local disk and from LocalStack S3 through DuckDB's httpfs extension, using the
 * S3 secret {@link DuckDBService} creates from {@code ducklake.s3.*}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class DuckDBServiceIntegrationTest {

    private static final String CSV = "id,name,value\n1,Item 1,100\n2,Item 2,200\n3,Item 3,300";
    private static final String TEST_TABLE = "test_table";

    @Autowired
    private DuckDBService duckDBService;

    @Autowired
    private S3Client s3Client;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() throws SQLException {
        duckDBService.executeStatement("DROP TABLE IF EXISTS " + TEST_TABLE);
    }

    @Test
    void testCreateTableFromS3Csv() throws SQLException {
        s3Client.putObject(PutObjectRequest.builder().bucket(DATA_BUCKET).key("service/test-data.csv").build(),
                RequestBody.fromString(CSV));

        duckDBService.createTableFromFile(TEST_TABLE, "s3://" + DATA_BUCKET + "/service/test-data.csv", "CSV");

        verifyTableData();
    }

    @Test
    void testCreateTableFromLocalCsv() throws SQLException, IOException {
        Path csv = Files.writeString(tempDir.resolve("test-data.csv"), CSV);

        duckDBService.createTableFromCSV(TEST_TABLE, csv.toString());

        verifyTableData();
    }

    @Test
    void testMissingS3ObjectFails() {
        assertThatThrownBy(() -> duckDBService.createTableFromFile(TEST_TABLE, "s3://" + DATA_BUCKET + "/missing.csv", "CSV"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testInTransactionRollsBackOnFailure() throws SQLException {
        duckDBService.executeStatement("CREATE TABLE " + TEST_TABLE + " (id INTEGER)");

        assertThatThrownBy(() -> duckDBService.inTransaction(conn -> {
            conn.createStatement().execute("INSERT INTO " + TEST_TABLE + " VALUES (1)");
            conn.createStatement().execute("INSERT INTO " + TEST_TABLE + " VALUES ('not a number')");
            return null;
        })).isInstanceOf(SQLException.class);

        assertThat(duckDBService.executeQuery("SELECT count(*) AS count FROM " + TEST_TABLE).getFirst().get("count"))
                .isEqualTo(0L);
    }

    private void verifyTableData() throws SQLException {
        List<Map<String, Object>> results = duckDBService.executeQuery("SELECT * FROM " + TEST_TABLE + " ORDER BY id");

        assertThat(results).hasSize(3);
        assertThat(results.getFirst()).containsEntry("name", "Item 1");
        assertThat(results.getFirst().get("id").toString()).isEqualTo("1");
        assertThat(results.getFirst().get("value").toString()).isEqualTo("100");
        assertThat(duckDBService.executeQuery("SELECT COUNT(*) AS count FROM " + TEST_TABLE).getFirst().get("count"))
                .isEqualTo(3L);
    }
}
