package com.rixon.learn.spring.data.unitycatalog;

import com.rixon.learn.spring.data.unitycatalog.model.*;
import com.rixon.learn.spring.data.unitycatalog.service.UnityCatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
public class UnityCatalogIntegrationTest {

    static GenericContainer<?> ucContainer = new GenericContainer<>(
            DockerImageName.parse("unitycatalog/unitycatalog:latest")
    )
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/api/2.1/unity-catalog/catalogs").forStatusCode(200));

    static {
        ucContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("unitycatalog.base-url", () ->
                String.format("http://%s:%d", ucContainer.getHost(), ucContainer.getMappedPort(8080)));
    }

    @Autowired
    private UnityCatalogService unityCatalogService;

    @Test
    @DisplayName("Verify Unity Catalog 3-level namespace: Catalogs, Schemas, Tables (Delta & Iceberg), and Volumes")
    void testUnityCatalogGovernanceLifecycle() {
        String catalogName = "capital_markets";
        String schemaName = "equities";

        // 1. Create Catalog
        UcCatalog catalog = unityCatalogService.createCatalog(catalogName, "Institutional trading catalog");
        assertThat(catalog).isNotNull();
        assertThat(catalog.getName()).isEqualTo(catalogName);

        // 2. List Catalogs
        List<UcCatalog> catalogs = unityCatalogService.listCatalogs();
        assertThat(catalogs).extracting(UcCatalog::getName).contains(catalogName);

        // 3. Create Schema
        UcSchema schema = unityCatalogService.createSchema(catalogName, schemaName, "Equities L2/L3 market data");
        assertThat(schema).isNotNull();
        assertThat(schema.getName()).isEqualTo(schemaName);
        assertThat(schema.getCatalogName()).isEqualTo(catalogName);

        // 4. Register Delta Lake Table
        List<UcColumn> tradeColumns = List.of(
                UcColumn.of("trade_id", "string", "STRING", 0, false),
                UcColumn.of("symbol", "string", "STRING", 1, false),
                UcColumn.of("price", "double", "DOUBLE", 2, false),
                UcColumn.of("quantity", "long", "LONG", 3, false)
        );
        UcTable deltaTable = unityCatalogService.createTable(catalogName, schemaName, "trades_delta", "DELTA", tradeColumns);
        assertThat(deltaTable).isNotNull();
        assertThat(deltaTable.getName()).isEqualTo("trades_delta");
        assertThat(deltaTable.getDataSourceFormat()).isEqualTo("DELTA");
        assertThat(deltaTable.getColumns()).hasSize(4);

        // 5. Register Parquet Table
        List<UcColumn> depthColumns = List.of(
                UcColumn.of("symbol", "string", "STRING", 0, false),
                UcColumn.of("bid_price", "double", "DOUBLE", 1, false),
                UcColumn.of("ask_price", "double", "DOUBLE", 2, false)
        );
        UcTable parquetTable = unityCatalogService.createTable(catalogName, schemaName, "orderbook_parquet", "PARQUET", depthColumns);
        assertThat(parquetTable).isNotNull();
        assertThat(parquetTable.getName()).isEqualTo("orderbook_parquet");
        assertThat(parquetTable.getDataSourceFormat()).isEqualTo("PARQUET");
        assertThat(parquetTable.getColumns()).hasSize(3);

        // 6. Create Volume for unstructured tick data
        UcVolume volume = unityCatalogService.createVolume(catalogName, schemaName, "raw_market_pcap", "EXTERNAL");
        assertThat(volume).isNotNull();
        assertThat(volume.getName()).isEqualTo("raw_market_pcap");
        assertThat(volume.getVolumeType()).isEqualTo("EXTERNAL");

        // 7. Verify Get Table by full 3-level name
        UcTable fetchedDelta = unityCatalogService.getTable(catalogName + "." + schemaName + ".trades_delta");
        assertThat(fetchedDelta).isNotNull();
        assertThat(fetchedDelta.getName()).isEqualTo("trades_delta");
    }
}
