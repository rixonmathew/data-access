package com.rixon.learn.spring.data;

import com.rixon.learn.spring.data.models.MovieRecord;
import com.rixon.learn.spring.data.models.PersonRecord;
import com.rixon.learn.spring.data.repository.MovieRepository;
import com.rixon.learn.spring.data.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit test for the demo CommandLineRunner without Spring context.
 */
public class Neo4JDataAccessApplicationTest {

    @Test
    public void testDemo() throws Exception {
        // Arrange mocks
        MovieRepository movieRepository = mock(MovieRepository.class);
        PersonRepository personRepository = mock(PersonRepository.class);

        List<MovieRecord> allMovies = new ArrayList<>();
        MovieRecord movie = new MovieRecord("some title", 2023, "tagline");
        allMovies.add(movie);

        List<PersonRecord> allPersons = new ArrayList<>();
        PersonRecord person = new PersonRecord("person 1", 2011);
        allPersons.add(person);

        when(movieRepository.findAll()).thenReturn(allMovies);
        when(personRepository.findAll()).thenReturn(allPersons);

        // Act
        CommandLineRunner demo = new Neo4JDataAccessApplication().demo(movieRepository, personRepository);
        demo.run();

        // Assert: The demo runner queries each repository exactly once
        verify(movieRepository, times(1)).findAll();
        verify(personRepository, times(1)).findAll();
    }
}