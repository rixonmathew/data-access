package com.rixon.learn.spring.data.age;

import com.rixon.learn.spring.data.age.cypher.AgtypeParser;
import com.rixon.learn.spring.data.age.cypher.Edge;
import com.rixon.learn.spring.data.age.cypher.GraphPath;
import com.rixon.learn.spring.data.age.cypher.Vertex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** agtype text exactly as AGE 1.8 prints it (copied from psql output). */
class AgtypeParserTest {

    @Test
    void parsesScalars() {
        assertThat(AgtypeParser.parse("\"A\"")).isEqualTo("A");
        assertThat(AgtypeParser.parse("42")).isEqualTo(42L);
        assertThat(AgtypeParser.parse("-7")).isEqualTo(-7L);
        assertThat(AgtypeParser.parse("2.5")).isEqualTo(2.5);
        assertThat(AgtypeParser.parse("1.50::numeric")).isEqualTo(new BigDecimal("1.50"));
        assertThat(AgtypeParser.parse("123456789012345678901234567890")).isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(AgtypeParser.parse("true")).isEqualTo(true);
        assertThat(AgtypeParser.parse("null")).isNull();
        assertThat((Double) AgtypeParser.parse("NaN")).isNaN();
        assertThat(AgtypeParser.parse("-Infinity")).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(AgtypeParser.parse(null)).isNull();
    }

    @Test
    void parsesMapsAndListsWithEscapes() {
        Object value = AgtypeParser.parse("{\"a\": 1, \"b\": [1, 2.5, \"x\", true, null], \"s\": \"line\\nq\\\"uote\\u00e9\"}");
        assertThat(value).isEqualTo(Map.of("a", 1L, "b", java.util.Arrays.asList(1L, 2.5, "x", true, null), "s", "line\nq\"uoteé"));
    }

    @Test
    void parsesVertexAndEdge() {
        Vertex vertex = (Vertex) AgtypeParser.parse(
                "{\"id\": 844424930131969, \"label\": \"Bank\", \"properties\": {\"name\": \"A\", \"tier\": 1}}::vertex");
        assertThat(vertex).isEqualTo(new Vertex(844424930131969L, "Bank", Map.of("name", "A", "tier", 1L)));

        Edge edge = (Edge) AgtypeParser.parse("{\"id\": 1125899906842627, \"label\": \"EXPOSED_TO\", \"end_id\": 844424930131970, "
                + "\"start_id\": 844424930131969, \"properties\": {\"amount\": 100}}::edge");
        assertThat(edge.startId()).isEqualTo(844424930131969L);
        assertThat(edge.endId()).isEqualTo(844424930131970L);
        assertThat(edge.property("amount")).isEqualTo(100L);
    }

    @Test
    void parsesPathIntoVerticesAndEdges() {
        GraphPath path = (GraphPath) AgtypeParser.parse("[{\"id\": 1, \"label\": \"Bank\", \"properties\": {\"name\": \"A\"}}::vertex, "
                + "{\"id\": 10, \"label\": \"EXPOSED_TO\", \"end_id\": 2, \"start_id\": 1, \"properties\": {}}::edge, "
                + "{\"id\": 2, \"label\": \"Bank\", \"properties\": {\"name\": \"B\"}}::vertex]::path");
        assertThat(path.length()).isEqualTo(1);
        assertThat(path.vertices()).extracting(v -> v.property("name")).containsExactly("A", "B");
    }

    @Test
    void annotationInsideStringIsPlainText() {
        assertThat(AgtypeParser.parse("{\"s\": \"x::vertex \\\"q\\\"\"}")).isEqualTo(Map.of("s", "x::vertex \"q\""));
        // A list of vertices (e.g. collect(v)) is a list, not a path
        assertThat(AgtypeParser.parse("[{\"id\": 1, \"label\": \"L\", \"properties\": {}}::vertex]"))
                .isEqualTo(List.of(new Vertex(1L, "L", Map.of())));
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> AgtypeParser.parse("{\"a\": 1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgtypeParser.parse("{}::unknown")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("::unknown");
        assertThatThrownBy(() -> AgtypeParser.parse("1 2")).isInstanceOf(IllegalArgumentException.class);
    }
}
