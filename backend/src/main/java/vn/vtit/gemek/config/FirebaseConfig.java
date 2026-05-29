/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase Admin SDK initialisation.
 *
 * <p>Initialises {@link FirebaseApp} on startup when {@code firebase.enabled=true}
 * and a valid credentials path is provided. When disabled, FCM calls are no-ops.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "firebase")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /**
     * Path to the Firebase service account JSON credentials file.
     * Injected from {@code FIREBASE_CREDENTIALS_PATH} environment variable.
     */
    private String credentialsPath;

    /**
     * Feature flag — set to {@code true} to enable FCM push notifications.
     * Defaults to {@code false} so the system starts cleanly without Firebase credentials.
     */
    private boolean enabled;

    /**
     * Initialises Firebase Admin SDK if enabled and not already initialised.
     *
     * <p>Skipped gracefully when disabled or credentials path is absent,
     * allowing the system to run without FCM in development/test environments.
     */
    @PostConstruct
    public void initialise() {
        // Skip initialisation if feature is disabled or credentials are not configured.
        if (!enabled || !StringUtils.hasText(credentialsPath)) {
            log.info("Firebase initialisation skipped — enabled={}, credentialsPath configured={}",
                    enabled, StringUtils.hasText(credentialsPath));
            return;
        }

        // Avoid re-initialising if a default app already exists.
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialised — skipping.");
            return;
        }

        try (InputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised successfully.");
        } catch (IOException ex) {
            // Log at ERROR level but do not prevent application startup.
            // FCM delivery will fail gracefully at runtime.
            log.error("Failed to initialise Firebase Admin SDK from path: {}. FCM push notifications will be unavailable.",
                    credentialsPath, ex);
        }
    }
}
