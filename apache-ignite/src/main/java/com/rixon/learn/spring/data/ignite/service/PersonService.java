package com.rixon.learn.spring.data.ignite.service;

import com.rixon.learn.spring.data.ignite.model.Person;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.springframework.stereotype.Service;

import javax.cache.Cache;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PersonService {

    private final IgniteCache<Long, Person> personCache;

    public void save(Person person) {
        personCache.put(person.getId(), person);
    }

    public Optional<Person> findById(Long id) {
        return Optional.ofNullable(personCache.get(id));
    }

    public List<Person> findAll() {
        List<Person> people = new ArrayList<>();
        for (Cache.Entry<Long, Person> entry : personCache) {
            people.add(entry.getValue());
        }
        return people;
    }

    public List<Person> findByLastName(String lastName) {
        SqlFieldsQuery sql = new SqlFieldsQuery(
                "SELECT * FROM Person WHERE lastName = ?").setArgs(lastName);
        
        List<Person> people = new ArrayList<>();
        try (QueryCursor<List<?>> cursor = personCache.query(sql)) {
            for (List<?> row : cursor) {
                Long id = (Long) row.get(0);
                Person person = personCache.get(id);
                if (person != null) {
                    people.add(person);
                }
            }
        }
        return people;
    }

    public void delete(Long id) {
        personCache.remove(id);
    }

    public void clear() {
        personCache.clear();
    }
}
