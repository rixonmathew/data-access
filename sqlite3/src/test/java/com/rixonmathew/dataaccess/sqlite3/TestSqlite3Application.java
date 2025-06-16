package com.rixonmathew.dataaccess.sqlite3;

import org.springframework.boot.SpringApplication;

public class TestSqlite3Application {

    public static void main(String[] args) {
        SpringApplication.from(Sqlite3Application::main).with(TestcontainersConfiguration.class).run(args);
    }

}
