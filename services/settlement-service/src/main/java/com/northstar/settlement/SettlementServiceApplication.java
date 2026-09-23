package com.northstar.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Settlement slice extracted from the Struts monolith per
 * docs/decisions/ADR-001-settlement-boundary.md: the three settlement request
 * flows served on port 8083 against HSQLDB in memory, loaded from the legacy
 * src/main/resources/db/schema.sql and seed.sql.
 */
@SpringBootApplication
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
