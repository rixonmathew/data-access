package com.rixonmathew.dataaccess.sqlite3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * Main application class for the SQLite3 module.
 * Excludes DataSourceAutoConfiguration since we're providing our own DataSource configuration.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class Sqlite3Application {

    public static void main(String[] args) {
        SpringApplication.run(Sqlite3Application.class, args);
    }

}
