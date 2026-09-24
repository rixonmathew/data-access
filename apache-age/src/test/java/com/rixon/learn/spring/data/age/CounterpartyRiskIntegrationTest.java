package com.rixon.learn.spring.data.age;

import com.rixon.learn.spring.data.age.model.Account;
import com.rixon.learn.spring.data.age.model.DownstreamBalance;
import com.rixon.learn.spring.data.age.model.Exposure;
import com.rixon.learn.spring.data.age.model.ExposureSummary;
import com.rixon.learn.spring.data.age.service.CounterpartyRiskService;
import com.rixon.learn.spring.data.age.cypher.CypherTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The neo4j module's three counterparty-risk scenarios on AGE, with the same data and the same expected
 * answers, followed by what AGE adds by living inside PostgreSQL.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
class CounterpartyRiskIntegrationTest {

    @Autowired
    private CounterpartyRiskService risk;

    @Autowired
    private CypherTemplate cypher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void clearGraph() {
        cypher.execute(risk.graph(), "MATCH (n) DETACH DELETE n", Map.of());
        jdbc.update("DELETE FROM public.account_balances");
    }

    // ---------------------------------------------------------------- the neo4j module's scenarios

    @Test
    @DisplayName("Scenario 1 (as neo4j): detect a circular counterparty exposure ring")
    void testCyclicRiskExposureAndContagion() {
        risk.saveNetwork(List.of(
                        new Account("ACC-GRAPH-001", "Hedge Fund Alpha", "INSTITUTIONAL"),
                        new Account("ACC-GRAPH-002", "Prime Broker Beta", "INSTITUTIONAL"),
                        new Account("ACC-GRAPH-003", "Clearing House Gamma", "INSTITUTIONAL")),
                List.of(new Exposure("ACC-GRAPH-001", "ACC-GRAPH-002", "CREDIT_LINE", 1_000_000.0, 750_000.0),
                        new Exposure("ACC-GRAPH-002", "ACC-GRAPH-003", "CLEARING_COLLATERAL", 800_000.0, 600_000.0),
                        new Exposure("ACC-GRAPH-003", "ACC-GRAPH-001", "SETTLEMENT_EXPOSURE", 500_000.0, 350_000.0)));

        assertThat(risk.findCyclicRiskExposure("ACC-GRAPH-001"))
                .containsExactlyInAnyOrder("ACC-GRAPH-001", "ACC-GRAPH-002", "ACC-GRAPH-003");
    }

    @Test
    @DisplayName("Scenario 2 (as neo4j): shortest contagion path, without shortestPath()")
    void testShortestRiskContagionPath() {
        linkChain("NODE-1", "NODE-2", "NODE-3", "NODE-4");                     // 3 hops
        linkChain("NODE-1", "NODE-X", "NODE-Y", "NODE-Z", "NODE-4");           // 4 hops, must lose

        assertThat(risk.findShortestRiskPath("NODE-1", "NODE-4", 10))
                .contains(List.of("NODE-1", "NODE-2", "NODE-3", "NODE-4"));
        // Like the neo4j query, edge direction is ignored
        assertThat(risk.findShortestRiskPath("NODE-4", "NODE-1", 10))
                .contains(List.of("NODE-4", "NODE-3", "NODE-2", "NODE-1"));
        // The hop bound is a hard limit: no path of at most 2 edges exists
        assertThat(risk.findShortestRiskPath("NODE-1", "NODE-4", 2)).isEmpty();
        assertThatThrownBy(() -> risk.findShortestRiskPath("NODE-1", "NODE-4", 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Scenario 3 (as neo4j): downstream blast radius")
    void testDownstreamContagionAccounts() {
        risk.saveNetwork(List.of(new Account("ROOT-0", "Major Bank", null),
                        new Account("SUB-1", "Subsidiary 1", null),
                        new Account("SUB-2", "Subsidiary 2", null)),
                List.of(new Exposure("ROOT-0", "SUB-1", "INTERCOMPANY", 200_000.0, 100_000.0),
                        new Exposure("ROOT-0", "SUB-2", "INTERCOMPANY", 300_000.0, 150_000.0)));

        assertThat(risk.findDownstreamContagionAccounts("ROOT-0")).containsExactlyInAnyOrder("SUB-1", "SUB-2");
    }

    // ---------------------------------------------------------------- AGE: writes

    @Test
    @DisplayName("MERGE is idempotent for vertices and edges; SET updates properties in place")
    void testMergeUpsertsInsteadOfDuplicating() {
        risk.upsertAccount(new Account("M-1", "Old Name", "RETAIL"));
        risk.upsertAccount(new Account("M-1", "New Name", "INSTITUTIONAL"));
        risk.upsertAccount(new Account("M-2", "Other", "RETAIL"));
        risk.upsertExposure(new Exposure("M-1", "M-2", "LOAN", 100.0, 10.0));
        risk.upsertExposure(new Exposure("M-1", "M-2", "LOAN", 100.0, 60.0));

        assertThat(risk.countAccounts()).isEqualTo(2);
        assertThat(risk.findAccountVertex("M-1")).hasValueSatisfying(v -> {
            assertThat(v.label()).isEqualTo("Account");
            assertThat(v.properties()).containsEntry("holderName", "New Name").containsEntry("accountType", "INSTITUTIONAL");
        });
        assertThat(risk.exposuresFrom("M-1")).containsExactly(new Exposure("M-1", "M-2", "LOAN", 100.0, 60.0));
    }

    @Test
    @DisplayName("SET/REMOVE a property and DETACH DELETE a vertex with its edges")
    void testPropertyUpdatesAndDetachDelete() {
        linkChain("D-1", "D-2", "D-3");
        risk.flagForReview("D-2", "limit breach");
        assertThat(risk.findAccountVertex("D-2")).hasValueSatisfying(v ->
                assertThat(v.properties()).containsEntry("reviewReason", "limit breach"));

        risk.clearReviewFlag("D-2");
        assertThat(risk.findAccountVertex("D-2")).hasValueSatisfying(v ->
                assertThat(v.properties()).doesNotContainKey("reviewReason"));

        risk.removeAccount("D-2");
        assertThat(risk.findAccountVertex("D-2")).isEmpty();
        assertThat(risk.exposuresFrom("D-1")).isEmpty();                       // its edges went with it
        assertThat(risk.findDownstreamContagionAccounts("D-1")).isEmpty();
    }

    @Test
    @DisplayName("Graph writes and relational writes share one PostgreSQL transaction")
    void testGraphAndRelationalWritesRollBackTogether() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            risk.upsertAccount(new Account("TX-1", "Rolled Back", "RETAIL"));
            risk.saveBalance("TX-1", new BigDecimal("1000.00"));
            assertThat(risk.findAccountVertex("TX-1")).isPresent();           // visible inside the transaction
            throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(risk.findAccountVertex("TX-1")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.account_balances WHERE account_number = 'TX-1'", Long.class))
                .isZero();
    }

    // ---------------------------------------------------------------- AGE: reads

    @Test
    @DisplayName("reduce() over path edges: the largest exposure chain reaching each downstream account")
    void testChainExposureWithReduce() {
        risk.saveNetwork(List.of(new Account("C-1", null, null), new Account("C-2", null, null),
                        new Account("C-3", null, null)),
                List.of(new Exposure("C-1", "C-2", "LOAN", 1_000.0, 400.0),
                        new Exposure("C-2", "C-3", "REPO", 1_000.0, 250.0),
                        new Exposure("C-1", "C-3", "SWAP", 1_000.0, 100.0)));

        Map<String, Double> chains = risk.maxChainExposure("C-1");
        assertThat(chains).containsOnlyKeys("C-2", "C-3");
        assertThat(chains.get("C-2")).isCloseTo(400.0, within(1e-9));
        assertThat(chains.get("C-3")).isCloseTo(650.0, within(1e-9));          // via C-2 beats the direct 100
    }

    @Test
    @DisplayName("Aggregation over edges: count and sum per relationship type")
    void testExposureAggregation() {
        risk.saveNetwork(List.of(new Account("A-1", null, null), new Account("A-2", null, null),
                        new Account("A-3", null, null)),
                List.of(new Exposure("A-1", "A-2", "LOAN", 100.0, 40.0),
                        new Exposure("A-2", "A-3", "LOAN", 100.0, 60.0),
                        new Exposure("A-3", "A-1", "REPO", 100.0, 25.0)));

        assertThat(risk.exposureByRelationshipType()).containsExactly(
                new ExposureSummary("LOAN", 2, 100.0), new ExposureSummary("REPO", 1, 25.0));
    }

    @Test
    @DisplayName("One SQL statement joins Cypher results with a relational table")
    void testGraphJoinedWithRelationalBalances() throws Exception {
        linkChain("H-0", "H-1", "H-2", "H-3", "H-4");                           // H-4 is 4 hops away: outside 1..3
        risk.saveBalance("H-1", new BigDecimal("500.00"));
        risk.saveBalance("H-2", new BigDecimal("1500.00"));
        risk.saveBalance("H-4", new BigDecimal("9999.00"));
        // H-3 has no balance row, so the inner join drops it

        List<DownstreamBalance> balances = risk.downstreamBalances("H-0");
        assertThat(balances).containsExactly(
                new DownstreamBalance("H-2", 2, new BigDecimal("1500.00")),
                new DownstreamBalance("H-1", 1, new BigDecimal("500.00")));
    }

    /** a -> b -> c ... with default exposure values. */
    private void linkChain(String... accounts) {
        for (String account : accounts) {
            risk.upsertAccount(new Account(account, "Bank " + account, null));
        }
        for (int i = 0; i + 1 < accounts.length; i++) {
            risk.upsertExposure(new Exposure(accounts[i], accounts[i + 1], "LOAN", 100_000.0, 50_000.0));
        }
    }
}
