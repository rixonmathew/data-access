package com.rixon.learn.spring.data;

import com.rixon.learn.spring.data.models.MovieRecord;
import com.rixon.learn.spring.data.models.PersonRecord;
import com.rixon.learn.spring.data.repository.MovieRepository;
import com.rixon.learn.spring.data.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Neo4JDataAccessApplicationTest class is used to test the functionality of Neo4JDataAccessApplication class.
 */
@SpringBootTest
public class Neo4JDataAccessApplicationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        CommandLineRunner demoTest(MovieRepository movieRepository, PersonRepository personRepository){
            return new Neo4JDataAccessApplication().demo(movieRepository, personRepository);
        }
    }

    @Autowired
    private CommandLineRunner demo;

    @MockBean
    private MovieRepository movieRepository;

    @MockBean
    private PersonRepository personRepository;

    @Test
    public void testDemo() throws Exception {
        List<MovieRecord> allMovies = new ArrayList<>();
        MovieRecord movie = new MovieRecord("some title",2023,"tagline");
        allMovies.add(movie);

        List<PersonRecord> allPersons = new ArrayList<>();
        PersonRecord person = new PersonRecord("person 1",2011);
        allPersons.add(person);
        
        when(movieRepository.findAll()).thenReturn(allMovies);
        when(personRepository.findAll()).thenReturn(allPersons);

        demo.run();

        verify(movieRepository, times(3)).findAll();
        verify(personRepository, times(3)).findAll();
    }
}