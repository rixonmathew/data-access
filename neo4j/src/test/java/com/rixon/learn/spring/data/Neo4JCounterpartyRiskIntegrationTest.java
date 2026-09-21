package com.rixon.learn.spring.data;

import com.rixon.learn.spring.data.models.AccountNode;
import com.rixon.learn.spring.data.repository.AccountNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@EnabledIfDockerAvailable
@ActiveProfiles("test")
public class Neo4JCounterpartyRiskIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.13.0")
            .withAdminPassword("secretPassword123");

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "secretPassword123");
    }

    @Autowired
    private AccountNodeRepository accountNodeRepository;

    @BeforeEach
    void cleanGraph() {
        accountNodeRepository.deleteAll();
    }

    @Test
    @DisplayName("Graph Scenario 1: Detect Circular Counterparty Exposure Ring (Contagion Risk)")
    void testCyclicRiskExposureAndContagion() {
        AccountNode accA = AccountNode.builder()
                .accountNumber("ACC-GRAPH-001")
                .holderName("Hedge Fund Alpha")
                .accountType("INSTITUTIONAL")
                .build();

        AccountNode accB = AccountNode.builder()
                .accountNumber("ACC-GRAPH-002")
                .holderName("Prime Broker Beta")
                .accountType("INSTITUTIONAL")
                .build();

        AccountNode accC = AccountNode.builder()
                .accountNumber("ACC-GRAPH-003")
                .holderName("Clearing House Gamma")
                .accountType("INSTITUTIONAL")
                .build();

        // Create cyclic chain: Alpha -> Beta -> Gamma -> Alpha
        accA.addExposure(accB, "CREDIT_LINE", 1_000_000.0, 750_000.0);
        accB.addExposure(accC, "CLEARING_COLLATERAL", 800_000.0, 600_000.0);
        accC.addExposure(accA, "SETTLEMENT_EXPOSURE", 500_000.0, 350_000.0);

        // Save all nodes
        accountNodeRepository.saveAll(List.of(accA, accB, accC));

        // Execute Cypher cycle detection query
        List<String> cycleNodes = accountNodeRepository.findCyclicRiskExposure("ACC-GRAPH-001");

        assertThat(cycleNodes).isNotEmpty();
        assertThat(cycleNodes).contains("ACC-GRAPH-001", "ACC-GRAPH-002", "ACC-GRAPH-003");
    }

    @Test
    @DisplayName("Graph Scenario 2: Trace Shortest Counterparty Contagion Path")
    void testShortestRiskContagionPath() {
        AccountNode node1 = AccountNode.builder().accountNumber("NODE-1").holderName("Bank 1").build();
        AccountNode node2 = AccountNode.builder().accountNumber("NODE-2").holderName("Bank 2").build();
        AccountNode node3 = AccountNode.builder().accountNumber("NODE-3").holderName("Bank 3").build();
        AccountNode node4 = AccountNode.builder().accountNumber("NODE-4").holderName("Bank 4").build();

        // Path: 1 -> 2 -> 3 -> 4
        node1.addExposure(node2, "LOAN", 100_000.0, 50_000.0);
        node2.addExposure(node3, "REPO", 100_000.0, 50_000.0);
        node3.addExposure(node4, "SWAP", 100_000.0, 50_000.0);

        accountNodeRepository.save(node3);
        accountNodeRepository.save(node2);
        accountNodeRepository.save(node1);
        accountNodeRepository.save(node4);

        List<String> path = accountNodeRepository.findShortestRiskPath("NODE-1", "NODE-4");

        assertThat(path).isNotNull();
        assertThat(path).containsExactly("NODE-1", "NODE-2", "NODE-3", "NODE-4");
    }

    @Test
    @DisplayName("Graph Scenario 3: Discover All Downstream Impacted Accounts (Blast Radius)")
    void testDownstreamContagionAccounts() {
        AccountNode root = AccountNode.builder().accountNumber("ROOT-0").holderName("Major Bank").build();
        AccountNode sub1 = AccountNode.builder().accountNumber("SUB-1").holderName("Subsidiary 1").build();
        AccountNode sub2 = AccountNode.builder().accountNumber("SUB-2").holderName("Subsidiary 2").build();

        root.addExposure(sub1, "INTERCOMPANY", 200_000.0, 100_000.0);
        root.addExposure(sub2, "INTERCOMPANY", 300_000.0, 150_000.0);

        accountNodeRepository.save(root);

        List<String> downstream = accountNodeRepository.findDownstreamContagionAccounts("ROOT-0");

        assertThat(downstream).containsExactlyInAnyOrder("SUB-1", "SUB-2");
    }
}
