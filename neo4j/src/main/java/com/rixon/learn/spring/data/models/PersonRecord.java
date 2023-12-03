package com.rixon.learn.spring.data.models;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Person")
public record PersonRecord(@Id String name, Integer born) {
}
