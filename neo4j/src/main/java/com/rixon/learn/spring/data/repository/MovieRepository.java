package com.rixon.learn.spring.data.repository;

import com.rixon.learn.spring.data.models.Movie;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface MovieRepository extends Neo4jRepository<Movie,String> {

    Movie findByTitle(String title);
}
