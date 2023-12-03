package com.rixon.learn.spring.data;

import com.rixon.learn.spring.data.models.MovieRecord;
import com.rixon.learn.spring.data.models.PersonRecord;
import com.rixon.learn.spring.data.repository.MovieRepository;
import com.rixon.learn.spring.data.repository.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

import java.util.List;

@SpringBootApplication
@EnableNeo4jRepositories
@EntityScan(basePackages = {"com.rixon.learn.spring.data.models"})
public class Neo4JDataAccessApplication {

    private final static Logger LOGGER = LoggerFactory.getLogger(Neo4JDataAccessApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(Neo4JDataAccessApplication.class, args);
    }

    @Bean CommandLineRunner demo(MovieRepository movieRepository, PersonRepository personRepository){
        return args -> {
            LOGGER.info("Querying movies");
            List<MovieRecord> allMovies = movieRepository.findAll();
            LOGGER.info("Found [{}] movies",allMovies.size());

            LOGGER.info("Querying persons");
            List<PersonRecord> allPersons = personRepository.findAll();
            LOGGER.info("Found [{}] persons",allPersons.size());
        };
    }

}
