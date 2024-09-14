package com.rixonmathew.duckdb;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DuckdbController {


    private final QueryService queryService;

    public DuckdbController(QueryService queryService) {
        this.queryService = queryService;
    }


    @GetMapping("/query")
    public List<Employee> query(String query) {
        return queryService.query(query);
    }
}
