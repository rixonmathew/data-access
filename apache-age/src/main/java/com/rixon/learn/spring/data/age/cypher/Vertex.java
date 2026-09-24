package com.rixon.learn.spring.data.age.cypher;

import java.util.Map;

/** A graph vertex as AGE returns it: {@code {"id": ..., "label": ..., "properties": {...}}::vertex}. */
public record Vertex(long id, String label, Map<String, Object> properties) {

    public Object property(String name) {
        return properties.get(name);
    }
}
