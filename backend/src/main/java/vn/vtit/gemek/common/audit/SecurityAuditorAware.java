/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.vtit.gemek.common.security.UserPrincipal;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies the current actor UUID to Spring Data JPA auditing
 * ({@code @CreatedBy} / {@code @LastModifiedBy}).
 *
 * <p>Resolution mirrors {@code AuditLogAspect.resolveActorId()}: read the
 * {@link org.springframework.security.core.context.SecurityContext}, and return the
 * {@link UserPrincipal} UUID when present. No {@code UserRepository} load — the principal
 * already carries the id.
 *
 * <p>Returns {@link Optional#empty()} when the request is unauthenticated or the principal
 * is not a {@link UserPrincipal} (scheduler / seed / Flyway / login flow). Empty leaves the
 * actor column {@code null} with no NPE.
 *
 * <p>Registered with bean name {@code auditorAware} to match
 * {@code @EnableJpaAuditing(auditorAwareRef = "auditorAware")}.
 */
@Component("auditorAware")
public class SecurityAuditorAware implements AuditorAware<UUID> {

    /**
     * Returns the current authenticated actor's UUID, or empty when none.
     *
     * @return the actor UUID, or {@link Optional#empty()} when unauthenticated.
     */
    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // No actor: system / scheduler / seed / Flyway / pre-auth login.
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        // Only a UserPrincipal carries a resolvable user UUID.
        if (principal instanceof UserPrincipal) {
            return Optional.ofNullable(((UserPrincipal) principal).getId());
        }
        return Optional.empty();
    }
}
