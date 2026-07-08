package com.gigledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the GigLedger Spring Boot application.
 *
 * @SpringBootApplication enables:
 *   - @ComponentScan  (finds all @Service, @Controller, @Repository, etc.)
 *   - @EnableAutoConfiguration (sets up DataSource, JPA, Security etc. from application.properties)
 *   - @Configuration
 */
@SpringBootApplication
public class GigLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GigLedgerApplication.class, args);
    }
}
