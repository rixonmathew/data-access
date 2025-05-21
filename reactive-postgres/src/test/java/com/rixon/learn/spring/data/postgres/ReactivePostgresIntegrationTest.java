package com.rixon.learn.spring.data.postgres;

import com.rixon.learn.spring.data.postgres.model.Person;
import com.rixon.learn.spring.data.postgres.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class ReactivePostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @LocalServerPort
    private int port;

    @Autowired
    private PersonRepository personRepository;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.host", postgres::getHost);
        registry.add("spring.r2dbc.port", postgres::getFirstMappedPort);
        registry.add("spring.r2dbc.database", postgres::getDatabaseName);
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void testCreateAndRetrievePerson() {
        // Create a test person
        Person testPerson = new Person();
        testPerson.setFirstName("John");
        testPerson.setLastName("Doe");
        testPerson.setEmail("john.doe@example.com");
        testPerson.setDateOfBirth(LocalDate.of(1990, 1, 1));
        testPerson.setAddress("123 Test Street");

        // Save the person via repository
        Mono<Person> savedPerson = personRepository.save(testPerson);

        StepVerifier.create(savedPerson)
                .assertNext(person -> {
                    assertThat(person.getId()).isNotNull();
                    assertThat(person.getFirstName()).isEqualTo("John");
                    assertThat(person.getLastName()).isEqualTo("Doe");
                    assertThat(person.getEmail()).isEqualTo("john.doe@example.com");
                })
                .verifyComplete();

        // Test GET endpoint
        webTestClient.get().uri("/persons")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Person.class)
                .value(persons -> {
                    assertThat(persons).isNotEmpty();
                    assertThat(persons).anyMatch(p -> 
                        p.getEmail().equals("john.doe@example.com") &&
                        p.getFirstName().equals("John") &&
                        p.getLastName().equals("Doe")
                    );
                });
    }

    @Test
    void testCreatePersonViaAPI() {
        // Create a test person
        Person testPerson = new Person();
        testPerson.setFirstName("Jane");
        testPerson.setLastName("Smith");
        testPerson.setEmail("jane.smith@example.com");
        testPerson.setDateOfBirth(LocalDate.of(1985, 5, 15));
        testPerson.setAddress("456 Test Avenue");

        // Test POST endpoint
        webTestClient.post().uri("/persons")
                .bodyValue(testPerson)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Person.class)
                .value(person -> {
                    assertThat(person.getId()).isNotNull();
                    assertThat(person.getFirstName()).isEqualTo("Jane");
                    assertThat(person.getLastName()).isEqualTo("Smith");
                    assertThat(person.getEmail()).isEqualTo("jane.smith@example.com");
                });

        // Verify the person was saved by querying by email
        webTestClient.get().uri("/persons/email/jane.smith@example.com")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Person.class)
                .value(persons -> {
                    assertThat(persons).hasSize(1);
                    assertThat(persons.get(0).getFirstName()).isEqualTo("Jane");
                    assertThat(persons.get(0).getLastName()).isEqualTo("Smith");
                });
    }
}