package com.rixon.learn.spring.data.dataaccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@RestController
public class QueryController {

    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    public QueryController(DataSource dataSource) {
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @GetMapping("/query/{count}")
    public ResponseEntity<String> query(@PathVariable("count") int count) {
        String sql = String.format("select * from tpch.sf1.customer limit %d",count);
        List<String> names = jdbcTemplate.query(sql, new RowMapper<String>() {
            @Override
            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getString("name");
            }
        });

        return ResponseEntity.ok(String.join(",", names));
    }
}
