package com.rixon.learn.spring.data.postgres;

import com.rixon.learn.spring.data.postgres.model.Person;
import com.rixon.learn.spring.data.postgres.repository.PersonRepository;
import com.rixon.learn.spring.data.postgres.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@SpringBootApplication
public class ReactivePostgresApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactivePostgresApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ReactivePostgresApplication.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction(PersonService personService) {
        return route(GET("/persons"),
                request -> ok().body(personService.findAll(), Person.class))
                .andRoute(GET("/persons/{id}"),
                        request -> ok().body(personService.findById(Long.valueOf(request.pathVariable("id"))), Person.class))
                .andRoute(GET("/persons/lastName/{lastName}"),
                        request -> ok().body(personService.findByLastName(request.pathVariable("lastName")), Person.class))
                .andRoute(GET("/persons/email/{email}"),
                        request -> ok().body(personService.findByEmail(request.pathVariable("email")), Person.class))
                .andRoute(POST("/persons").and(accept(MediaType.APPLICATION_JSON)), serverRequest -> {
                    Mono<Person> body = serverRequest.body(BodyExtractors.toMono(Person.class));
                    return ok().body(personService.save(body), Person.class);
                })
                .andRoute(DELETE("/persons/{id}"),
                        request -> ok().body(personService.deleteById(Long.valueOf(request.pathVariable("id"))), Void.class));
    }

    @Bean
    CommandLineRunner commandLineRunner(PersonRepository personRepository) {
        return args -> {
            LOGGER.info("Initializing database with sample data");
            
            // Delete all existing persons
            personRepository.deleteAll()
                .doOnSuccess(v -> LOGGER.info("Deleted all existing persons"))
                .doOnError(e -> LOGGER.error("Error deleting persons", e))
                .subscribe();
            
            // Create sample persons
            Flux<Person> persons = Flux.fromStream(
                IntStream.range(1, 11).mapToObj(i -> {
                    Person person = new Person();
                    person.setFirstName("FirstName" + i);
                    person.setLastName("LastName" + i);
                    person.setEmail("person" + i + "@example.com");
                    person.setDateOfBirth(LocalDate.now().minusYears(20 + i));
                    person.setAddress("Address " + i);
                    return person;
                })
            );
            
            // Save sample persons
            personRepository.saveAll(persons)
                .doOnComplete(() -> LOGGER.info("Sample data initialization completed"))
                .doOnError(e -> LOGGER.error("Error initializing sample data", e))
                .subscribe();
        };
    }
}