package com.rixon.learn.spring.data.ignite.service;

import com.rixon.learn.spring.data.ignite.model.Person;
import org.apache.ignite.IgniteCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.cache.Cache;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private IgniteCache<Long, Person> personCache;

    private PersonService personService;

    @BeforeEach
    void setUp() {
        personService = new PersonService(personCache);
    }

    @Test
    void shouldSaveAndRetrievePerson() {
        // Given
        Person person = new Person(1L, "John", "Doe", 30, "john.doe@example.com");
        when(personCache.get(1L)).thenReturn(person);

        // When
        personService.save(person);
        Optional<Person> retrieved = personService.findById(1L);

        // Then
        assertTrue(retrieved.isPresent());
        assertEquals("John", retrieved.get().getFirstName());
        assertEquals("Doe", retrieved.get().getLastName());
        assertEquals(30, retrieved.get().getAge());
        assertEquals("john.doe@example.com", retrieved.get().getEmail());
        verify(personCache).put(1L, person);
    }

    @Test
    void shouldReturnEmptyOptionalForNonExistentId() {
        // Given
        when(personCache.get(999L)).thenReturn(null);

        // When
        Optional<Person> retrieved = personService.findById(999L);

        // Then
        assertFalse(retrieved.isPresent());
    }

    @Test
    void shouldFindAllPersons() {
        // Given
        Person person1 = new Person(1L, "John", "Doe", 30, "john.doe@example.com");
        Person person2 = new Person(2L, "Jane", "Smith", 25, "jane.smith@example.com");
        
        // Mock the iterator behavior
        Iterator<Cache.Entry<Long, Person>> iterator = mock(Iterator.class);
        when(iterator.hasNext()).thenReturn(true, true, false);
        
        Cache.Entry<Long, Person> entry1 = mock(Cache.Entry.class);
        when(entry1.getValue()).thenReturn(person1);
        
        Cache.Entry<Long, Person> entry2 = mock(Cache.Entry.class);
        when(entry2.getValue()).thenReturn(person2);
        
        when(iterator.next()).thenReturn(entry1, entry2);
        
        when(personCache.iterator()).thenReturn(iterator);

        // When
        List<Person> allPersons = personService.findAll();

        // Then
        assertEquals(2, allPersons.size());
        assertTrue(allPersons.contains(person1));
        assertTrue(allPersons.contains(person2));
    }

    @Test
    void shouldDeletePerson() {
        // When
        personService.delete(1L);

        // Then
        verify(personCache).remove(1L);
    }

    @Test
    void shouldClearAllPersons() {
        // When
        personService.clear();

        // Then
        verify(personCache).clear();
    }
}
