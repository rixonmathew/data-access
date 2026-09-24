package com.rixon.learn.spring.data.age.cypher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses AGE's {@code agtype} text output: JSON plus an optional {@code ::vertex}, {@code ::edge},
 * {@code ::path} or {@code ::numeric} annotation after any value, and the float tokens {@code NaN},
 * {@code Infinity} and {@code -Infinity}.
 * <p>
 * Result types: {@link Vertex}, {@link Edge}, {@link GraphPath}, {@code Map}, {@code List}, {@code String},
 * {@code Long} (or {@code BigInteger} if larger), {@code Double}, {@code BigDecimal} for {@code ::numeric},
 * {@code Boolean}, or {@code null}. Annotations are only recognised outside quoted strings, so a property
 * value such as {@code "a::vertex"} stays a plain string.
 */
public final class AgtypeParser {

    private final String text;
    private int pos;

    private AgtypeParser(String text) {
        this.text = text;
    }

    public static Object parse(String agtype) {
        if (agtype == null) {
            return null;
        }
        AgtypeParser parser = new AgtypeParser(agtype);
        Object value = parser.value();
        parser.skipWhitespace();
        if (parser.pos != agtype.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object value() {
        skipWhitespace();
        if (pos >= text.length()) {
            throw error("Unexpected end of input");
        }
        char c = text.charAt(pos);
        Object value;
        boolean number = false;
        if (c == '{') {
            value = object();
        } else if (c == '[') {
            value = array();
        } else if (c == '"') {
            value = string();
        } else if (text.startsWith("true", pos)) {
            pos += 4;
            value = Boolean.TRUE;
        } else if (text.startsWith("false", pos)) {
            pos += 5;
            value = Boolean.FALSE;
        } else if (text.startsWith("null", pos)) {
            pos += 4;
            value = null;
        } else if (text.startsWith("NaN", pos)) {
            pos += 3;
            value = Double.NaN;
        } else if (text.startsWith("Infinity", pos)) {
            pos += 8;
            value = Double.POSITIVE_INFINITY;
        } else if (text.startsWith("-Infinity", pos)) {
            pos += 9;
            value = Double.NEGATIVE_INFINITY;
        } else {
            value = numberToken();
            number = true;
        }
        return annotate(value, annotation(), number);
    }

    /** Reads an optional {@code ::name} suffix. */
    private String annotation() {
        if (!text.startsWith("::", pos)) {
            return null;
        }
        pos += 2;
        int start = pos;
        while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    @SuppressWarnings("unchecked")
    private Object annotate(Object value, String annotation, boolean number) {
        if (number) {
            String token = (String) value;
            if ("numeric".equals(annotation)) {
                return new BigDecimal(token);
            }
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return Double.valueOf(token);
            }
            BigInteger integer = new BigInteger(token);
            return integer.bitLength() < 64 ? (Object) integer.longValue() : integer;
        }
        if (annotation == null) {
            return value;
        }
        return switch (annotation) {
            case "vertex" -> toVertex((Map<String, Object>) value);
            case "edge" -> toEdge((Map<String, Object>) value);
            case "path" -> toPath((List<Object>) value);
            default -> throw error("Unknown agtype annotation ::" + annotation);
        };
    }

    @SuppressWarnings("unchecked")
    private static Vertex toVertex(Map<String, Object> map) {
        return new Vertex(((Number) map.get("id")).longValue(), (String) map.get("label"),
                (Map<String, Object>) map.getOrDefault("properties", Map.of()));
    }

    @SuppressWarnings("unchecked")
    private static Edge toEdge(Map<String, Object> map) {
        return new Edge(((Number) map.get("id")).longValue(), (String) map.get("label"),
                ((Number) map.get("start_id")).longValue(), ((Number) map.get("end_id")).longValue(),
                (Map<String, Object>) map.getOrDefault("properties", Map.of()));
    }

    private static GraphPath toPath(List<Object> elements) {
        List<Vertex> vertices = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (Object element : elements) {
            if (element instanceof Vertex vertex) {
                vertices.add(vertex);
            } else if (element instanceof Edge edge) {
                edges.add(edge);
            } else {
                throw new IllegalArgumentException("Path element is neither vertex nor edge: " + element);
            }
        }
        return new GraphPath(List.copyOf(vertices), List.copyOf(edges));
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            expect(':');
            map.put(key, value());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value());
            skipWhitespace();
            if (peek() == ',') {
                pos++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> throw error("Invalid escape \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private String numberToken() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("Unexpected character '" + text.charAt(pos) + "'");
        }
        return text.substring(start, pos);
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unexpected end of input");
        }
        return text.charAt(pos);
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos + " in agtype: " + text);
    }
}
