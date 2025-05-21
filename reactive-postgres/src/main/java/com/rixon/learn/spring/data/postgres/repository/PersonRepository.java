package com.rixon.learn.spring.data.postgres.repository;

import com.rixon.learn.spring.data.postgres.model.Person;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PersonRepository extends R2dbcRepository<Person, Long> {
    
    Flux<Person> findByLastName(String lastName);
    
    Flux<Person> findByEmail(String email);
}