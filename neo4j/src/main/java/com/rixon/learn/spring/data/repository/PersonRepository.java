package com.rixon.learn.spring.data.repository;

import com.rixon.learn.spring.data.models.PersonRecord;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PersonRepository extends Neo4jRepository<PersonRecord,String> {
}
