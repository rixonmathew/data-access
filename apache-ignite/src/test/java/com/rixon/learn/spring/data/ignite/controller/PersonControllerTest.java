package com.rixon.learn.spring.data.ignite.controller;

import com.rixon.learn.spring.data.ignite.model.Person;
import com.rixon.learn.spring.data.ignite.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonControllerTest {

    @Mock
    private PersonService personService;

    @InjectMocks
    private PersonController personController;

    private Person person1;
    private Person person2;

    @BeforeEach
    void setUp() {
        person1 = new Person(1L, "John", "Doe", 30, "john.doe@example.com");
        person2 = new Person(2L, "Jane", "Smith", 25, "jane.smith@example.com");
    }

    @Test
    void shouldCreatePerson() {
        // When
        ResponseEntity<Void> response = personController.createPerson(person1);

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(personService).save(person1);
    }

    @Test
    void shouldGetPersonById() {
        // Given
        when(personService.findById(1L)).thenReturn(Optional.of(person1));

        // When
        ResponseEntity<Person> response = personController.getPersonById(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(person1, response.getBody());
    }

    @Test
    void shouldReturnNotFoundForNonExistentPerson() {
        // Given
        when(personService.findById(999L)).thenReturn(Optional.empty());

        // When
        ResponseEntity<Person> response = personController.getPersonById(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void shouldGetAllPersons() {
        // Given
        List<Person> people = Arrays.asList(person1, person2);
        when(personService.findAll()).thenReturn(people);

        // When
        ResponseEntity<List<Person>> response = personController.getAllPersons();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(people, response.getBody());
    }

    @Test
    void shouldFindPersonsByLastName() {
        // Given
        when(personService.findByLastName("Doe")).thenReturn(Collections.singletonList(person1));

        // When
        ResponseEntity<List<Person>> response = personController.findByLastName("Doe");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(person1, response.getBody().get(0));
    }

    @Test
    void shouldDeletePerson() {
        // When
        ResponseEntity<Void> response = personController.deletePerson(1L);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(personService).delete(1L);
    }

    @Test
    void shouldDeleteAllPersons() {
        // When
        ResponseEntity<Void> response = personController.deleteAllPersons();

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(personService).clear();
    }
}
