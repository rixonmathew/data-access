package com.rixon.learn.spring.data.ignite.controller;

import com.rixon.learn.spring.data.ignite.model.Person;
import com.rixon.learn.spring.data.ignite.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;

    @PostMapping
    public ResponseEntity<Void> createPerson(@RequestBody Person person) {
        personService.save(person);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Person> getPersonById(@PathVariable Long id) {
        return personService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Person>> getAllPersons() {
        return ResponseEntity.ok(personService.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Person>> findByLastName(@RequestParam String lastName) {
        return ResponseEntity.ok(personService.findByLastName(lastName));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePerson(@PathVariable Long id) {
        personService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllPersons() {
        personService.clear();
        return ResponseEntity.noContent().build();
    }
}
