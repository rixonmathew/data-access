package com.rixon.learn.spring.data.age;

import com.rixon.learn.spring.data.age.cypher.CypherTemplate;
import com.rixon.learn.spring.data.age.cypher.Edge;
import com.rixon.learn.spring.data.age.cypher.GraphPath;
import com.rixon.learn.spring.data.age.cypher.Vertex;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** openCypher coverage in AGE 1.8, how graphs are stored, and where AGE differs from Neo4j. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfDockerAvailable
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CypherFeaturesIntegrationTest {

    private static final String GRAPH = "cypher_features";

    @Autowired
    private CypherTemplate cypher;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void createGraph() {
        cypher.dropGraph(GRAPH);
        cypher.createGraph(GRAPH);
        cypher.execute(GRAPH, """
                CREATE (alice:Person {name: 'Alice', age: 34}),
                       (bob:Person {name: 'Bob', age: 29}),
                       (carol:Person {name: 'Carol', age: 41}),
                       (acme:Company {name: 'Acme'}),
                       (alice)-[:KNOWS {since: 2019}]->(bob),
                       (bob)-[:KNOWS {since: 2021}]->(carol),
                       (alice)-[:WORKS_AT {role: 'Engineer'}]->(acme),
                       (carol)-[:WORKS_AT {role: 'Manager'}]->(acme)""", Map.of());
    }

    @AfterAll
    void dropGraph() {
        cypher.dropGraph(GRAPH);
    }

    @Test
    @DisplayName("Vertices, edges and paths come back as typed objects with consistent ids")
    void testVertexEdgeAndPathResults() {
        Map<String, Object> row = cypher.query(GRAPH, """
                MATCH p = (a:Person {name: 'Alice'})-[k:KNOWS]->(b:Person)
                RETURN a, k, b, p""", "a", "k", "b", "p").getFirst();
        Vertex alice = (Vertex) row.get("a");
        Edge knows = (Edge) row.get("k");
        Vertex bob = (Vertex) row.get("b");
        GraphPath path = (GraphPath) row.get("p");

        assertThat(alice.label()).isEqualTo("Person");
        assertThat(alice.properties()).containsEntry("name", "Alice").containsEntry("age", 34L);
        assertThat(knows.label()).isEqualTo("KNOWS");
        assertThat(knows.startId()).isEqualTo(alice.id());
        assertThat(knows.endId()).isEqualTo(bob.id());
        assertThat(knows.property("since")).isEqualTo(2019L);
        assertThat(path.vertices()).containsExactly(alice, bob);
        assertThat(path.edges()).containsExactly(knows);
    }

    @Test
    @DisplayName("MERGE: ON CREATE SET works, ON MATCH SET fails in AGE 1.8.0; MERGE + SET is the workaround")
    void testMergeOnCreateAndOnMatch() {
        String onCreateOnMatch = """
                MERGE (c:Company {name: $name})
                ON CREATE SET c.seen = 1
                ON MATCH SET c.seen = c.seen + 1
                RETURN c.seen""";
        // First run creates the vertex: ON CREATE SET applies
        assertThat(cypher.<Long>queryForList(GRAPH, onCreateOnMatch, Map.of("name", "Globex"), "seen")).containsExactly(1L);
        // Second run matches it: AGE 1.8.0 fails inside ON MATCH SET
        assertThatThrownBy(() -> cypher.query(GRAPH, onCreateOnMatch, Map.of("name", "Globex"), "seen"))
                .isInstanceOfSatisfying(BadSqlGrammarException.class, e -> assertThat(e.getMostSpecificCause().getMessage())
                        .contains("attribute 1 of type record has wrong type"));

        // Workaround: MERGE, then an unconditional SET that handles both cases
        String mergeThenSet = """
                MERGE (c:Company {name: $name})
                SET c.seen = coalesce(c.seen, 0) + 1
                RETURN c.seen""";
        assertThat(cypher.<Long>queryForList(GRAPH, mergeThenSet, Map.of("name", "Globex"), "seen")).containsExactly(2L);
        assertThat(cypher.<Long>queryForList(GRAPH, mergeThenSet, Map.of("name", "Initech"), "seen")).containsExactly(1L);
        cypher.execute(GRAPH, "MATCH (c:Company) WHERE c.name IN ['Globex', 'Initech'] DELETE c", Map.of());
    }

    @Test
    @DisplayName("OPTIONAL MATCH, WITH, UNWIND, collect, list comprehension, CASE, ORDER BY")
    void testQueryClauses() {
        assertThat(cypher.query(GRAPH, """
                MATCH (p:Person)
                OPTIONAL MATCH (p)-[w:WORKS_AT]->(c:Company)
                RETURN p.name, c.name ORDER BY p.name""", "person", "company"))
                .containsExactly(row("person", "Alice", "company", "Acme"),
                        row("person", "Bob", "company", null),                      // no employer: NULL, row kept
                        row("person", "Carol", "company", "Acme"));

        assertThat(cypher.query(GRAPH, """
                MATCH (p:Person)
                WITH collect(p.name) AS names, avg(p.age) AS average
                UNWIND names AS name
                WITH name, average WHERE name <> 'Bob'
                RETURN name, CASE WHEN name = 'Carol' THEN 'senior' ELSE 'staff' END,
                       [x IN range(1, 3) | x * 10]
                ORDER BY name DESC""", "name", "level", "tens"))
                .containsExactly(
                        Map.of("name", "Carol", "level", "senior", "tens", List.of(10L, 20L, 30L)),
                        Map.of("name", "Alice", "level", "staff", "tens", List.of(10L, 20L, 30L)));
    }

    @Test
    @DisplayName("Parameters: strings, numbers and lists in one agtype map, reused across executions")
    void testParameters() {
        String query = "MATCH (p:Person) WHERE p.name IN $names AND p.age >= $minAge RETURN p.name ORDER BY p.name";
        // More executions than pgjdbc's prepareThreshold (5), so later runs use a server-side prepared statement
        for (int run = 0; run < 8; run++) {
            assertThat(cypher.<String>queryForList(GRAPH, query,
                    Map.of("names", List.of("Alice", "Bob", "Carol"), "minAge", 30), "name"))
                    .containsExactly("Alice", "Carol");
        }
    }

    @Test
    @DisplayName("Parameter binding: agtype PGobject works with or without a cast; varchar fails either way")
    void testParameterBindingRules() throws Exception {
        String query = "MATCH (p:Person {name: $name}) RETURN p.age";
        String bare = cypher.sql(GRAPH, query, true, "age");
        String cast = bare.replace("$$, ?)", "$$, ?::agtype)");
        String json = "{\"name\": \"Alice\"}";

        for (String sql : List.of(bare, cast)) {
            assertThat(jdbc.queryForList(sql, String.class, cypher.parameters(Map.of("name", "Alice")))).containsExactly("34");
        }
        // Spring maps these two server errors to different DataAccessException subclasses
        assertThatThrownBy(() -> jdbc.queryForList(bare, String.class, json))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.getMostSpecificCause().getMessage())
                        .contains("function ag_catalog.cypher(unknown, unknown, character varying) does not exist"));
        assertThatThrownBy(() -> jdbc.queryForList(cast, String.class, json))
                .isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.getMostSpecificCause().getMessage())
                        .contains("third argument of cypher function must be a parameter"));
    }

    @Test
    @DisplayName("Variable-length paths: bounded hops, direction, and path functions")
    void testVariableLengthPaths() {
        assertThat(cypher.query(GRAPH, """
                MATCH p = (a:Person {name: 'Alice'})-[:KNOWS*1..2]->(x:Person)
                RETURN x.name, length(p), [n IN nodes(p) | n.name] ORDER BY length(p)""", "name", "hops", "names"))
                .containsExactly(
                        Map.of("name", "Bob", "hops", 1L, "names", List.of("Alice", "Bob")),
                        Map.of("name", "Carol", "hops", 2L, "names", List.of("Alice", "Bob", "Carol")));
        // Undirected: Carol reaches Alice only by walking KNOWS edges backwards
        assertThat(cypher.<String>queryForList(GRAPH,
                "MATCH (c:Person {name: 'Carol'})-[:KNOWS*1..2]-(x:Person {name: 'Alice'}) RETURN x.name", Map.of(), "name"))
                .containsExactly("Alice");
    }

    @Test
    @DisplayName("Neo4j differences: no shortestPath(), no multiple labels, Cypher text cannot contain $$")
    void testDifferencesFromNeo4j() {
        assertThatThrownBy(() -> cypher.query(GRAPH, """
                MATCH p = shortestPath((a:Person {name: 'Alice'})-[:KNOWS*]-(c:Person {name: 'Carol'})) RETURN p""", "p"))
                .isInstanceOfSatisfying(BadSqlGrammarException.class, e -> assertThat(e.getMostSpecificCause().getMessage())
                        .contains("syntax error at or near \"shortestPath\""));
        assertThatThrownBy(() -> cypher.execute(GRAPH, "CREATE (:Person:Employee {name: 'Dave'})", Map.of()))
                .isInstanceOf(BadSqlGrammarException.class);
        assertThatThrownBy(() -> cypher.query(GRAPH, "RETURN '$$'", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("$$");
    }

    @Test
    @DisplayName("Storage: a graph is a schema, each label an ordinary table")
    void testGraphIsStoredAsPostgresTables() {
        assertThat(jdbc.queryForList("""
                SELECT l.name, l.kind, l.relation::text AS relation FROM ag_catalog.ag_label l
                JOIN ag_catalog.ag_graph g ON g.graphid = l.graph
                WHERE g.name = ? AND l.name NOT LIKE '\\_ag%' ORDER BY l.name""", GRAPH))
                .containsExactly(
                        Map.of("name", "Company", "kind", "v", "relation", "cypher_features.\"Company\""),
                        Map.of("name", "KNOWS", "kind", "e", "relation", "cypher_features.\"KNOWS\""),
                        Map.of("name", "Person", "kind", "v", "relation", "cypher_features.\"Person\""),
                        Map.of("name", "WORKS_AT", "kind", "e", "relation", "cypher_features.\"WORKS_AT\""));

        // Plain SQL over the label table sees the same vertices Cypher does
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cypher_features.\"Person\"", Long.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM cypher_features."KNOWS" k
                JOIN cypher_features."Person" p ON p.id = k.start_id""", Long.class)).isEqualTo(2);
    }

    /** A result row; unlike Map.of it allows null values. */
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    @Test
    @DisplayName("Session setup: AGE preloaded, public first on the search_path")
    void testConnectionSetup() {
        assertThat(jdbc.queryForObject("SHOW shared_preload_libraries", String.class)).contains("age");
        assertThat(jdbc.queryForObject("SHOW search_path", String.class)).startsWith("public");
        assertThat(jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname = 'age'", String.class))
                .isEqualTo("1.8.0");
    }
}
