package com.rixon.learn.spring.data.ignite.service;

import com.rixon.learn.spring.data.ignite.config.IgniteConfig;
import com.rixon.learn.spring.data.ignite.model.Person;
import org.apache.ignite.Ignite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link PersonService#findByLastName} as real Ignite SQL on an embedded node built by the
 * application's own {@link IgniteConfig}, so it exercises whichever SQL engine the app configures.
 */
class PersonServiceSqlIntegrationTest {

    private static Ignite ignite;
    private static PersonService personService;

    @BeforeAll
    static void startNode() {
        IgniteConfig config = new IgniteConfig();
        ignite = config.igniteInstance();
        personService = new PersonService(config.personCache(ignite));
        personService.save(new Person(1L, "John", "Doe", 30, "john.doe@example.com"));
        personService.save(new Person(2L, "Jane", "Doe", 28, "jane.doe@example.com"));
        personService.save(new Person(3L, "Max", "Smith", 41, "max.smith@example.com"));
    }

    @AfterAll
    static void stopNode() {
        ignite.close();
    }

    @Test
    void shouldFindPeopleByLastNameWithSql() {
        List<Person> does = personService.findByLastName("Doe");

        assertThat(does).extracting(Person::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(does).extracting(Person::getFirstName).containsExactlyInAnyOrder("John", "Jane");
        assertThat(personService.findByLastName("Smith")).extracting(Person::getId).containsExactly(3L);
        assertThat(personService.findByLastName("Nobody")).isEmpty();
    }
}
