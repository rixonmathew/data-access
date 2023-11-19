package com.rixon.learn.spring.data.repository;

import com.rixon.learn.spring.data.models.Person;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PersonRepository extends Neo4jRepository<Person,String> {
}
