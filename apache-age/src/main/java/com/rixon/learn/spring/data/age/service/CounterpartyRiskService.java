package com.rixon.learn.spring.data.age.service;

import com.rixon.learn.spring.data.age.cypher.CypherTemplate;
import com.rixon.learn.spring.data.age.cypher.GraphPath;
import com.rixon.learn.spring.data.age.cypher.Vertex;
import com.rixon.learn.spring.data.age.model.Account;
import com.rixon.learn.spring.data.age.model.DownstreamBalance;
import com.rixon.learn.spring.data.age.model.Exposure;
import com.rixon.learn.spring.data.age.model.ExposureSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The counterparty exposure network from the {@code neo4j} module, stored in PostgreSQL through AGE:
 * {@code (:Account)-[:HAS_EXPOSURE_TO]->(:Account)}. The same questions (contagion rings, shortest contagion
 * path, downstream blast radius) plus things only a graph-inside-Postgres can do, such as joining graph
 * results with relational tables in one SQL statement and one transaction.
 */
@Slf4j
@Service
public class CounterpartyRiskService {

    public static final String ACCOUNT = "Account";
    public static final String EXPOSURE = "HAS_EXPOSURE_TO";

    private final CypherTemplate cypher;
    private final JdbcTemplate jdbc;
    private final String graph;

    public CounterpartyRiskService(CypherTemplate cypher, JdbcTemplate jdbc, @Value("${age.graph}") String graph) {
        this.cypher = cypher;
        this.jdbc = jdbc;
        this.graph = CypherTemplate.identifier(graph);
        initialize();
    }

    /** Idempotent: extension, graph, labels, property indexes and the relational balances table. */
    private void initialize() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS age");
        cypher.createGraph(graph);
        cypher.createVertexLabel(graph, ACCOUNT);
        cypher.createEdgeLabel(graph, EXPOSURE);
        // {accountNumber: $x} patterns compile to `properties @> {...}` (GIN); WHERE a.accountNumber = $x compiles
        // to an accessor expression (btree expression index). Label tables live in the graph's schema.
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS account_properties_gin ON %s."Account" USING gin (properties)""".formatted(graph));
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS account_number_idx ON %s."Account"
                USING btree (ag_catalog.agtype_access_operator(VARIADIC ARRAY[properties, '"accountNumber"'::ag_catalog.agtype]))"""
                .formatted(graph));
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS public.account_balances (
                    account_number TEXT PRIMARY KEY,
                    balance        NUMERIC(18, 2) NOT NULL)""");
        log.info("AGE graph '{}' ready", graph);
    }

    public String graph() {
        return graph;
    }

    // ---------------------------------------------------------------- writes

    /** MERGE on the account number: creates the vertex once, updates its properties afterwards. */
    public void upsertAccount(Account account) {
        cypher.execute(graph, """
                MERGE (a:Account {accountNumber: $accountNumber})
                SET a.holderName = $holderName, a.accountType = $accountType""",
                params("accountNumber", account.accountNumber(), "holderName", account.holderName(),
                        "accountType", account.accountType()));
    }

    /** Creates or updates the single exposure edge between two existing accounts. */
    public void upsertExposure(Exposure exposure) {
        cypher.execute(graph, """
                MATCH (a:Account {accountNumber: $from}), (b:Account {accountNumber: $to})
                MERGE (a)-[e:HAS_EXPOSURE_TO]->(b)
                SET e.relationshipType = $type, e.exposureLimit = $limit, e.currentExposure = $current""",
                params("from", exposure.fromAccount(), "to", exposure.toAccount(), "type", exposure.relationshipType(),
                        "limit", exposure.exposureLimit(), "current", exposure.currentExposure()));
    }

    /** Accounts and exposures in one database transaction: all of it is stored, or none of it. */
    @Transactional
    public void saveNetwork(List<Account> accounts, List<Exposure> exposures) {
        accounts.forEach(this::upsertAccount);
        exposures.forEach(this::upsertExposure);
    }

    /** SET adds a property, REMOVE deletes it; both on the stored vertex. */
    public void flagForReview(String accountNumber, String reason) {
        cypher.execute(graph, "MATCH (a:Account {accountNumber: $acc}) SET a.reviewReason = $reason",
                params("acc", accountNumber, "reason", reason));
    }

    public void clearReviewFlag(String accountNumber) {
        cypher.execute(graph, "MATCH (a:Account {accountNumber: $acc}) REMOVE a.reviewReason", params("acc", accountNumber));
    }

    /** DETACH DELETE removes the vertex and every edge touching it. */
    public void removeAccount(String accountNumber) {
        cypher.execute(graph, "MATCH (a:Account {accountNumber: $acc}) DETACH DELETE a", params("acc", accountNumber));
    }

    public void saveBalance(String accountNumber, BigDecimal balance) {
        jdbc.update("""
                INSERT INTO public.account_balances (account_number, balance) VALUES (?, ?)
                ON CONFLICT (account_number) DO UPDATE SET balance = EXCLUDED.balance""", accountNumber, balance);
    }

    // ---------------------------------------------------------------- reads

    public Optional<Vertex> findAccountVertex(String accountNumber) {
        return cypher.<Vertex>queryForList(graph, "MATCH (a:Account {accountNumber: $acc}) RETURN a",
                params("acc", accountNumber), "a").stream().findFirst();
    }

    public List<Exposure> exposuresFrom(String accountNumber) {
        return cypher.query(graph, """
                        MATCH (a:Account {accountNumber: $acc})-[e:HAS_EXPOSURE_TO]->(b:Account)
                        RETURN b.accountNumber, e.relationshipType, e.exposureLimit, e.currentExposure
                        ORDER BY b.accountNumber""",
                params("acc", accountNumber), "target", "type", "exposure_limit", "current_exposure").stream()
                .map(row -> new Exposure(accountNumber, (String) row.get("target"), (String) row.get("type"),
                        ((Number) row.get("exposure_limit")).doubleValue(), ((Number) row.get("current_exposure")).doubleValue()))
                .toList();
    }

    /**
     * Accounts on any exposure ring that starts and ends at {@code accountNumber} (2 to 6 hops), as in the
     * neo4j module's {@code findCyclicRiskExposure}.
     */
    public List<String> findCyclicRiskExposure(String accountNumber) {
        return cypher.queryForList(graph, """
                MATCH path = (a:Account {accountNumber: $acc})-[:HAS_EXPOSURE_TO*2..6]->(a)
                UNWIND nodes(path) AS n
                RETURN DISTINCT n.accountNumber""", params("acc", accountNumber), "account");
    }

    /**
     * The shortest connection between two accounts, ignoring edge direction.
     * <p>
     * AGE has no {@code shortestPath()}: this enumerates every path of up to {@code maxHops} edges and keeps the
     * shortest. Cost grows exponentially with {@code maxHops} on dense graphs, so the bound is required.
     */
    public Optional<List<String>> findShortestRiskPath(String source, String target, int maxHops) {
        if (maxHops < 1 || maxHops > 10) {
            throw new IllegalArgumentException("maxHops must be between 1 and 10");
        }
        return cypher.<GraphPath>queryForList(graph, """
                        MATCH p = (s:Account {accountNumber: $source})-[:HAS_EXPOSURE_TO*1..%d]-(t:Account {accountNumber: $target})
                        RETURN p ORDER BY length(p) LIMIT 1""".formatted(maxHops),
                params("source", source, "target", target), "p").stream()
                .findFirst()
                .map(path -> path.vertices().stream().map(v -> (String) v.property("accountNumber")).toList());
    }

    /** Every account reachable downstream within 3 hops (the neo4j module's blast radius query). */
    public List<String> findDownstreamContagionAccounts(String accountNumber) {
        return cypher.queryForList(graph, """
                MATCH (a:Account {accountNumber: $acc})-[:HAS_EXPOSURE_TO*1..3]->(downstream:Account)
                RETURN DISTINCT downstream.accountNumber""", params("acc", accountNumber), "account");
    }

    /**
     * For each downstream account, the largest exposure chain reaching it: the sum of currentExposure along the
     * path, computed in Cypher with {@code reduce} over the path's edges.
     */
    public Map<String, Double> maxChainExposure(String accountNumber) {
        Map<String, Double> result = new LinkedHashMap<>();
        cypher.query(graph, """
                        MATCH (a:Account {accountNumber: $acc})-[rs:HAS_EXPOSURE_TO*1..3]->(d:Account)
                        WITH d.accountNumber AS account, reduce(total = 0.0, r IN rs | total + r.currentExposure) AS chain
                        RETURN account, max(chain) ORDER BY account""",
                        params("acc", accountNumber), "account", "exposure")
                .forEach(row -> result.put((String) row.get("account"), ((Number) row.get("exposure")).doubleValue()));
        return result;
    }

    /** Aggregation across all edges: count and total exposure per relationship type. */
    public List<ExposureSummary> exposureByRelationshipType() {
        return cypher.query(graph, """
                        MATCH ()-[e:HAS_EXPOSURE_TO]->()
                        RETURN e.relationshipType, count(e), sum(e.currentExposure)
                        ORDER BY e.relationshipType""", "type", "edges", "total").stream()
                .map(row -> new ExposureSummary((String) row.get("type"), ((Number) row.get("edges")).longValue(),
                        ((Number) row.get("total")).doubleValue()))
                .toList();
    }

    /**
     * Graph and relational data in one SQL statement: the downstream accounts from Cypher, joined with
     * {@code public.account_balances} and sorted by balance. {@code agtype_to_text} turns the agtype string
     * into plain text for the join.
     */
    public List<DownstreamBalance> downstreamBalances(String accountNumber) throws SQLException {
        String graphSql = cypher.sql(graph, """
                MATCH p = (a:Account {accountNumber: $acc})-[:HAS_EXPOSURE_TO*1..3]->(d:Account)
                RETURN d.accountNumber, min(length(p))""", true, "account", "hops");
        return jdbc.query("""
                        SELECT ag_catalog.agtype_to_text(g.account) AS account_number, (g.hops)::int AS hops, b.balance
                        FROM (%s) g
                        JOIN public.account_balances b ON b.account_number = ag_catalog.agtype_to_text(g.account)
                        ORDER BY b.balance DESC""".formatted(graphSql),
                (rs, rowNum) -> new DownstreamBalance(rs.getString("account_number"), rs.getInt("hops"), rs.getBigDecimal("balance")),
                cypher.parameters(params("acc", accountNumber)));
    }

    public long countAccounts() {
        return ((Number) cypher.queryForList(graph, "MATCH (a:Account) RETURN count(a)", Map.of(), "n").getFirst()).longValue();
    }

    private static Map<String, Object> params(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
