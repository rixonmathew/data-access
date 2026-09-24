package com.rixon.learn.spring.data.age.cypher;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runs openCypher through AGE's {@code cypher()} SQL function over plain JDBC.
 * <p>
 * AGE embeds Cypher in SQL:
 * <pre>
 * SELECT * FROM ag_catalog.cypher('graph', $$ MATCH (a {name: $name}) RETURN a $$, ?) AS (a agtype)
 * </pre>
 * <ul>
 *   <li>The Cypher text is dollar-quoted, so it must not contain {@code $$}. Values never go into the text:
 *       they are passed as one agtype map and referenced as {@code $name}.</li>
 *   <li>That map must be bound as a parameter whose type is {@code agtype} (a {@link PGobject}). A value bound
 *       as varchar fails: without a cast no {@code cypher()} overload matches, and with {@code ?::agtype} AGE
 *       sees an expression instead of a parameter.</li>
 *   <li>SQL needs the result shape up front, so every call names its returned columns ({@code AS (a agtype)}).</li>
 * </ul>
 */
@Component
public class CypherTemplate {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public CypherTemplate(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Runs a Cypher query and returns one map per row, keyed by {@code columns}, with parsed agtype values. */
    public List<Map<String, Object>> query(String graph, String cypher, Map<String, ?> parameters, String... columns) {
        String sql = sql(graph, cypher, !parameters.isEmpty(), columns);
        return jdbc.query(sql, ps -> {
            if (!parameters.isEmpty()) {
                ps.setObject(1, parameters(parameters));
            }
        }, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.length; i++) {
                row.put(columns[i], AgtypeParser.parse(rs.getString(i + 1)));
            }
            return row;
        });
    }

    public List<Map<String, Object>> query(String graph, String cypher, String... columns) {
        return query(graph, cypher, Map.of(), columns);
    }

    /** Runs a single-column query and returns that column's values. */
    @SuppressWarnings("unchecked")
    public <T> List<T> queryForList(String graph, String cypher, Map<String, ?> parameters, String column) {
        return query(graph, cypher, parameters, column).stream().map(row -> (T) row.get(column)).toList();
    }

    /** Runs a Cypher statement that returns nothing (CREATE, SET, DELETE without RETURN). */
    public void execute(String graph, String cypher, Map<String, ?> parameters) {
        query(graph, cypher, parameters, "result");
    }

    /** The SQL AGE runs for this Cypher, e.g. for EXPLAIN or for joining with relational tables. */
    public String sql(String graph, String cypher, boolean withParameters, String... columns) {
        if (cypher.contains("$$")) {
            throw new IllegalArgumentException("Cypher text must not contain $$ (it is dollar-quoted inside SQL)");
        }
        if (columns.length == 0) {
            throw new IllegalArgumentException("At least one result column is required");
        }
        List<String> columnList = new ArrayList<>();
        for (String column : columns) {
            columnList.add(identifier(column) + " agtype");
        }
        return "SELECT * FROM ag_catalog.cypher('%s', $$ %s $$%s) AS (%s)".formatted(
                identifier(graph), cypher, withParameters ? ", ?" : "", String.join(", ", columnList));
    }

    // ---------------------------------------------------------------- graph administration

    public boolean graphExists(String graph) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?)", Boolean.class, identifier(graph)));
    }

    /** Creates the graph (a PostgreSQL schema of the same name) if it does not exist. */
    public void createGraph(String graph) {
        if (!graphExists(graph)) {
            jdbc.execute("SELECT ag_catalog.create_graph('%s')".formatted(identifier(graph)));
        }
    }

    /** Drops the graph and its schema, including every label table. */
    public void dropGraph(String graph) {
        if (graphExists(graph)) {
            jdbc.execute("SELECT ag_catalog.drop_graph('%s', true)".formatted(identifier(graph)));
        }
    }

    public boolean labelExists(String graph, String label) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM ag_catalog.ag_label l JOIN ag_catalog.ag_graph g ON g.graphid = l.graph
                               WHERE g.name = ? AND l.name = ?)""", Boolean.class, identifier(graph), identifier(label)));
    }

    /** Creates a vertex label (a table inheriting from {@code _ag_label_vertex}) if it does not exist. */
    public void createVertexLabel(String graph, String label) {
        if (!labelExists(graph, label)) {
            jdbc.execute("SELECT ag_catalog.create_vlabel('%s', '%s')".formatted(identifier(graph), identifier(label)));
        }
    }

    /** Creates an edge label (a table with {@code start_id}/{@code end_id} columns) if it does not exist. */
    public void createEdgeLabel(String graph, String label) {
        if (!labelExists(graph, label)) {
            jdbc.execute("SELECT ag_catalog.create_elabel('%s', '%s')".formatted(identifier(graph), identifier(label)));
        }
    }

    /**
     * Bulk-loads vertices from a CSV file under the server's AGE load directory ({@code /tmp/age/} in the
     * apache/age image). Every CSV value becomes a <em>string</em> property, including the {@code id} column.
     */
    public void loadVerticesFromFile(String graph, String label, String fileName) {
        jdbc.queryForList("SELECT ag_catalog.load_labels_from_file(?, ?, ?)", identifier(graph), identifier(label), fileName);
    }

    /** Bulk-loads edges; the CSV's start_id/end_id refer to the {@code id} column of the loaded vertex files. */
    public void loadEdgesFromFile(String graph, String label, String fileName) {
        jdbc.queryForList("SELECT ag_catalog.load_edges_from_file(?, ?, ?)", identifier(graph), identifier(label), fileName);
    }

    public static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid identifier: " + name);
        }
        return name;
    }

    /** The parameter map as the agtype bind value {@code cypher()} expects, for SQL built around {@link #sql}. */
    public PGobject parameters(Map<String, ?> parameters) throws SQLException {
        PGobject object = new PGobject();
        object.setType("agtype");
        object.setValue(json.writeValueAsString(parameters));
        return object;
    }
}
