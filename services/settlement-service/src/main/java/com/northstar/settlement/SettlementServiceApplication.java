package com.northstar.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the extracted settlement module (SPEC-SETTLE-001).
 */
@SpringBootApplication
public class SettlementServiceApplication {

    protected SettlementServiceApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
