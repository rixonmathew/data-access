package com.rixon.learn.spring.data.postgres.service;

import com.rixon.learn.spring.data.postgres.model.Person;
import com.rixon.learn.spring.data.postgres.repository.PersonRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Flux<Person> findAll() {
        return personRepository.findAll();
    }

    public Mono<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    public Flux<Person> findByLastName(String lastName) {
        return personRepository.findByLastName(lastName);
    }

    public Flux<Person> findByEmail(String email) {
        return personRepository.findByEmail(email);
    }

    public Mono<Person> save(Mono<Person> personMono) {
        return personMono.flatMap(personRepository::save);
    }

    public Mono<Void> deleteById(Long id) {
        return personRepository.deleteById(id);
    }
}