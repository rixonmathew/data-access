package com.rixon.learn.spring.data.age.cypher;

import java.util.List;

/** A path: vertices and edges alternating, starting and ending with a vertex ({@code [...]::path}). */
public record GraphPath(List<Vertex> vertices, List<Edge> edges) {

    /** Number of edges, as Cypher's {@code length(p)}. */
    public int length() {
        return edges.size();
    }
}
