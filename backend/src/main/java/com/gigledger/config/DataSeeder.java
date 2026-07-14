package com.gigledger.config;

import com.gigledger.entity.PlatformGrievanceContact;
import com.gigledger.repository.PlatformGrievanceContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds default platforms and runs manual schema migrations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PlatformGrievanceContactRepository contactRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Running schema migrations...");
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN DEFAULT TRUE NOT NULL");
            jdbcTemplate.execute("ALTER TABLE users DROP COLUMN IF EXISTS sms_notifications_enabled");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS district VARCHAR(64) DEFAULT 'Chennai'");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS state VARCHAR(64) DEFAULT 'Tamil Nadu'");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(32) DEFAULT 'bike'");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS fuel_efficiency DECIMAL(5,2) DEFAULT 45.00");
            log.info("Schema migrations executed successfully.");
        } catch (Exception e) {
            log.error("Schema migration failed: {}", e.getMessage(), e);
        }

        if (contactRepository.count() == 0) {
            log.info("Seeding PlatformGrievanceContact table...");

            PlatformGrievanceContact swiggy = PlatformGrievanceContact.builder()
                    .platformName("Swiggy")
                    .grievanceEmail("grievances@swiggy.in")
                    .verified(true)
                    .contactNotes("General grievance redressal, per their published Stakeholder Engagement and Grievance Redressal Policy.")
                    .legalBasisNote("IT Rules 2021 Rule 3(2) grievance officer requirement")
                    .build();

            PlatformGrievanceContact zomato = PlatformGrievanceContact.builder()
                    .platformName("Zomato")
                    .grievanceEmail("grievance@zomato.com")
                    .verified(true)
                    .contactNotes("Published Grievance Officer contact.")
                    .legalBasisNote("IT Rules 2021 Rule 3(2) grievance officer requirement")
                    .build();

            PlatformGrievanceContact blinkit = PlatformGrievanceContact.builder()
                    .platformName("Blinkit")
                    .grievanceEmail("contact pending verification")
                    .verified(false)
                    .contactNotes("Awaiting verification of official grievance email channel.")
                    .build();

            PlatformGrievanceContact uber = PlatformGrievanceContact.builder()
                    .platformName("Uber")
                    .grievanceEmail("contact pending verification")
                    .verified(false)
                    .contactNotes("Awaiting verification of official grievance email channel.")
                    .build();

            PlatformGrievanceContact ola = PlatformGrievanceContact.builder()
                    .platformName("Ola")
                    .grievanceEmail("contact pending verification")
                    .verified(false)
                    .contactNotes("Awaiting verification of official grievance email channel.")
                    .build();

            PlatformGrievanceContact rapido = PlatformGrievanceContact.builder()
                    .platformName("Rapido")
                    .grievanceEmail("contact pending verification")
                    .verified(false)
                    .contactNotes("Awaiting verification of official grievance email channel.")
                    .build();

            contactRepository.saveAll(List.of(swiggy, zomato, blinkit, uber, ola, rapido));
            log.info("Seeded 6 platform contacts successfully.");
        } else {
            log.info("PlatformGrievanceContact table already has data, skipping seed.");
        }
    }
}
