package com.rixon.learn.spring.data.ignite.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Person implements Serializable {

    @QuerySqlField(index = true)
    private Long id;

    @QuerySqlField(index = true)
    private String firstName;

    @QuerySqlField(index = true)
    private String lastName;

    @QuerySqlField
    private int age;

    @QuerySqlField
    private String email;
}
