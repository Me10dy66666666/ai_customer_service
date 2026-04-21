package com.example.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFixer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaFixer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            logger.info("Attempting to fix database schema constraints...");
            // Fix: Allow user_id to be null for visitor profiles
            jdbcTemplate.execute("ALTER TABLE user_profiles MODIFY COLUMN user_id BIGINT NULL");
            logger.info("Successfully altered table user_profiles: user_id is now nullable.");
        } catch (Exception e) {
            logger.warn("Schema fix execution warning (might already be applied or table not found): {}", e.getMessage());
        }
    }
}
