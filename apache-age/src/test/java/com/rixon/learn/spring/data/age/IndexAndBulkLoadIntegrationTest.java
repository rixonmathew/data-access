package com.rixon.learn.spring.data.age;

import com.rixon.learn.spring.data.age.cypher.CypherTemplate;
import com.rixon.learn.spring.data.age.model.Account;
import com.rixon.learn.spring.data.age.service.CounterpartyRiskService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** How Cypher predicates map onto PostgreSQL indexes, and bulk loading from CSV. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexAndBulkLoadIntegrationTest {

    private static final String IMPORT_GRAPH = "bulk_import";

    @Autowired
    private CounterpartyRiskService risk;

    @Autowired
    private CypherTemplate cypher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @AfterAll
    void dropImportGraph() {
        cypher.dropGraph(IMPORT_GRAPH);
    }

    @Test
    @DisplayName("Property-map patterns use the GIN index; WHERE equality uses the btree expression index")
    void testCypherPredicatesUsePostgresIndexes() {
        cypher.execute(risk.graph(), "MATCH (n) DETACH DELETE n", Map.of());
        for (int i = 0; i < 20; i++) {
            risk.upsertAccount(new Account("IDX-" + i, "Bank " + i, "RETAIL"));
        }

        // {accountNumber: '...'} compiles to `properties @> {...}` (containment), which GIN indexes
        assertThat(explain("MATCH (a:Account {accountNumber: 'IDX-7'}) RETURN a", true))
                .contains("account_properties_gin").contains("@>");
        // WHERE a.accountNumber = '...' compiles to an accessor expression, matched by the btree expression index
        assertThat(explain("MATCH (a:Account) WHERE a.accountNumber = 'IDX-7' RETURN a", true))
                .contains("account_number_idx").contains("agtype_access_operator");
        // With age.enable_containment off, the map pattern also compiles to the accessor form
        assertThat(explain("MATCH (a:Account {accountNumber: 'IDX-7'}) RETURN a", false))
                .contains("account_number_idx").doesNotContain("@>");
    }

    /** EXPLAIN in one transaction on one connection, with sequential scans discouraged (the table is tiny). */
    private String explain(String query, boolean containment) {
        return transactions.execute(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            jdbc.execute("SET LOCAL age.enable_containment = " + (containment ? "on" : "off"));
            List<String> plan = jdbc.queryForList("EXPLAIN " + cypher.sql(risk.graph(), query, false, "a"), String.class);
            return String.join("\n", plan);
        });
    }

    @Test
    @DisplayName("CSV bulk load: vertices and edges from files, every property loaded as a string")
    void testBulkLoadFromCsv() {
        cypher.dropGraph(IMPORT_GRAPH);
        cypher.createGraph(IMPORT_GRAPH);
        cypher.createVertexLabel(IMPORT_GRAPH, "ImportedAccount");
        cypher.createEdgeLabel(IMPORT_GRAPH, "IMPORTED_EXPOSURE");
        // Bare file names, resolved under /tmp/age/ inside the database container
        cypher.loadVerticesFromFile(IMPORT_GRAPH, "ImportedAccount", "accounts.csv");
        cypher.loadEdgesFromFile(IMPORT_GRAPH, "IMPORTED_EXPOSURE", "exposures.csv");

        List<Map<String, Object>> rows = cypher.query(IMPORT_GRAPH, """
                MATCH (a:ImportedAccount)-[e:IMPORTED_EXPOSURE]->(b:ImportedAccount)
                RETURN a.accountNumber, a.tier, e.currentExposure, b.accountNumber
                ORDER BY a.accountNumber""", "source", "tier", "exposure", "target");
        assertThat(rows).containsExactly(
                Map.of("source", "IMP-001", "tier", "1", "exposure", "250000", "target", "IMP-002"),
                Map.of("source", "IMP-002", "tier", "2", "exposure", "125000.50", "target", "IMP-003"));

        // Numbers must be converted in Cypher before arithmetic or numeric comparison
        assertThat(cypher.query(IMPORT_GRAPH, """
                MATCH (a:ImportedAccount)-[e:IMPORTED_EXPOSURE]->()
                WHERE toInteger(a.tier) <= 2
                RETURN sum(toFloat(e.currentExposure)), max(toInteger(a.tier))""", "total", "max_tier"))
                .containsExactly(Map.of("total", 375000.5, "max_tier", 2L));
    }
}
