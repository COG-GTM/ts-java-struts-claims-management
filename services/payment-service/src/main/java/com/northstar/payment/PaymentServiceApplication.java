package com.northstar.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment slice extracted from the Struts monolith per
 * docs/decisions/ADR-002-payment-boundary.md: the four payment request flows,
 * served on port 8084 against HSQLDB in memory loaded from the legacy
 * src/main/resources/db/schema.sql and seed.sql.
 */
@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
