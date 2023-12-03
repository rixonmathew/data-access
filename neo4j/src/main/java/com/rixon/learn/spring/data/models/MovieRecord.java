package com.rixon.learn.spring.data.models;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Movie")
public record MovieRecord(@Id String title, Integer released, String tagline) {
}
