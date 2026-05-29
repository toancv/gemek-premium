/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for the Gemek Premium Apartment Management System.
 *
 * <p>Enables async execution for fire-and-forget notification dispatch and
 * scheduled jobs for SLA monitoring and contract expiry alerts.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GemekApplication {

    /**
     * Main method — launches the Spring Boot application.
     *
     * @param args command-line arguments passed by the JVM.
     */
    public static void main(String[] args) {
        SpringApplication.run(GemekApplication.class, args);
    }
}
