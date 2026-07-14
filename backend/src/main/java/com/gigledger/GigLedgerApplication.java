package com.gigledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the GigLedger Spring Boot application.
 *
 * @SpringBootApplication enables:
 *   - @ComponentScan  (finds all @Service, @Controller, @Repository, etc.)
 *   - @EnableAutoConfiguration (sets up DataSource, JPA, Security etc. from application.properties)
 *   - @Configuration
 */
@SpringBootApplication
@EnableScheduling
public class GigLedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(GigLedgerApplication.class, args);
    }
}
