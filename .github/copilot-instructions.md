# Copilot Instructions for data-access Repository

## Project Overview

This is a multi-module Maven project demonstrating Spring Boot data access patterns across 14+ different databases and technologies. Each module is self-contained but depends on a shared `commons` module.

**Architecture:**
- **Parent POM**: Root `pom.xml` defines shared properties (Java 25, Spring Boot 4.0.1, TestContainers, AWS SDK)
- **Commons Module**: Shared utilities, models, and base configurations used by all database modules
- **Database Modules**: Independent implementations for each data store (H2, Cassandra, MongoDB, Neo4j, DynamoDB, PostgreSQL, etc.)
- **Reactive Modules**: Dedicated modules for reactive/async access patterns (reactive-postgres, reactive-h2, reactive-cassandra, etc.)

## Build, Test & Lint

### Build & Test Commands

**Full build with tests** (includes timezone for reproducibility):
```bash
mvn -D"user.timezone=Asia/Kolkata" clean install
```

**Build without tests:**
```bash
mvn clean install -DskipTests
```

**Run all tests in the project:**
```bash
mvn test
```

**Run tests for a specific module:**
```bash
mvn test -pl h2
mvn test -pl cassandra
```

**Run a single test class:**
```bash
mvn test -Dtest=ContractRepositoryTests
```

**Run a specific test method:**
```bash
mvn test -Dtest=ContractRepositoryTests#testSaveContract
```

**Run integration tests (failsafe):**
```bash
mvn verify
```

**Run tests for specific module with integration tests:**
```bash
mvn verify -pl h2
```

### Key Test Configuration

- **Test Framework**: JUnit 5 with Spring Test context
- **Test Pattern**: Classes ending in `*Tests` or `*Test` (e.g., `ContractRepositoryTests.java`)
- **Mocking**: Mockito (configured with javaagent in Surefire/Failsafe plugins)
- **Spring Integration**: `@ExtendWith(SpringExtension.class)`, `@ContextConfiguration`, `@TestPropertySource`
- **Test Containers**: Used for database integration tests (version 2.0.2)

### Linting & Code Quality

No dedicated linting tools are configured. The project uses:
- **Compiler Plugin**: Maven compiler (Java 25)
- **Annotation Processing**: Lombok (auto-generates getters, setters, constructors)

## Code Conventions

### Module Structure

Each module follows this layout:
```
module-name/
├── pom.xml                      # Module-specific dependencies
├── src/main/java/              # Production code
│   └── com/rixon/learn/spring/data/
│       ├── [ModuleName]DataAccessApplication.java   # Spring Boot main class
│       ├── repository/          # Spring Data repositories (JPA/Reactive)
│       ├── service/             # Business logic
│       └── controller/          # REST endpoints (if applicable)
└── src/test/java/              # Tests
    └── com/rixon/learn/spring/data/
        ├── [ModuleName]Tests.java              # Integration tests
        └── PersistenceTestConfig.java          # Spring test configuration
```

### Dependencies

- **All modules depend on `commons`**: Import common models and utilities from `com.rixon.model`
- **Database-specific**: Each module brings its own driver/client (h2, cassandra-driver, mongo-java-driver, etc.)
- **Spring Boot Starters**: 
  - `spring-boot-starter-data-jpa` for relational + commons
  - `spring-boot-starter-webflux` for REST endpoints
  - Database-specific Data starters (spring-data-cassandra, spring-data-mongodb, etc.)

### Naming Conventions

- **Packages**: `com.rixon.learn.spring.data.[module-name]`
- **Entity/Model Classes**: Use `@Data` (Lombok) for automatic getters/setters
- **Repository Interfaces**: Spring Data repository interfaces (extend `CrudRepository`, `ReactiveRepository`, etc.)
- **Service Classes**: Business logic with `@Service` annotation
- **Tests**: Suffix with `Tests` or `Test` (e.g., `ContractRepositoryTests`)

### Spring Boot Configuration

- **Application Entry Point**: Each module has a `[DatabaseName]DataAccessApplication` class with `@SpringBootApplication`
- **Test Configuration**: Separate `PersistenceTestConfig` classes for test-specific beans (example: H2 module uses in-memory database for tests)
- **Test Properties**: Override via `@TestPropertySource` on test classes:
  ```java
  @TestPropertySource(properties = {
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false"
  })
  ```

### Shared Utilities (Commons Module)

The `commons` module provides:
- **Models/Entities**: Shared domain objects used across modules
- **Utilities**: `DataGeneratorUtils` for creating test data (e.g., `randomContracts(count)`)
- **Base Configurations**: Shared Spring Data configurations

Always import utilities from `com.rixon.model` when working with test data.

### Reactor/Reactive Patterns

Reactive modules (reactive-postgres, reactive-h2, reactive-cassandra) use:
- `ReactiveRepository` interfaces instead of `CrudRepository`
- `Mono`/`Flux` return types (Project Reactor)
- R2DBC drivers for relational databases
- Reactive clients for NoSQL databases

## Special Notes

- **Java Version**: Java 25 (modern LTS alternative); ensure local JDK matches
- **Timezone Handling**: Build uses `Asia/Kolkata` timezone; include `-D"user.timezone=Asia/Kolkata"` for consistency
- **Mockito Agent**: Tests require mockito javaagent; Surefire/Failsafe plugins handle this automatically
- **Spring Boot Version**: 4.0.1 (recent major version); check compatibility when upgrading dependencies

## Recommended MCP Servers

### Maven MCP Server
For faster builds and test execution integration:
1. Install the Maven MCP server for your AI assistant
2. Configure it to point to your local Maven installation
3. Use it to run builds, tests, and verify modules without manual command execution

This is particularly useful when working on multiple database modules simultaneously, as it enables faster feedback loops.
