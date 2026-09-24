package com.rixon.learn.spring.data.age.cypher;

import java.util.Map;

/** A graph edge as AGE returns it: {@code {"id", "label", "start_id", "end_id", "properties"}::edge}. */
public record Edge(long id, String label, long startId, long endId, Map<String, Object> properties) {

    public Object property(String name) {
        return properties.get(name);
    }
}
