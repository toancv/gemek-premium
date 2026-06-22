/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.vtit.gemek.module.user.entity.User;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security {@link UserDetails} implementation holding the authenticated user's
 * UUID, phone number, and role as a {@link GrantedAuthority}.
 *
 * <p>Stored in the {@link org.springframework.security.core.context.SecurityContext}
 * after successful JWT verification. Service methods access it via
 * {@link org.springframework.security.core.context.SecurityContextHolder}.
 */
public class UserPrincipal implements UserDetails {

    /** The user's unique identifier. */
    private final UUID id;

    /** The user's canonical phone number — used as the Spring Security username. */
    private final String phone;

    /**
     * The user's hashed password.
     * Stored to support {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}.
     */
    private final String passwordHash;

    /** Spring Security authorities derived from the user's single role. */
    private final List<GrantedAuthority> authorities;

    /** Whether the user account is active. */
    private final boolean active;

    /**
     * Constructs a {@link UserPrincipal} from a {@link User} entity.
     *
     * @param user the user entity loaded from the database.
     */
    public UserPrincipal(User user) {
        this.id = user.getId();
        this.phone = user.getPhone();
        this.passwordHash = user.getPasswordHash();
        // Convention: role name is stored without ROLE_ prefix in DB; Spring Security expects ROLE_ prefix.
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.active = user.isActive();
    }

    /**
     * Returns the user's UUID.
     *
     * @return the user's unique identifier.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the user's canonical phone number.
     *
     * @return the phone number.
     */
    public String getPhone() {
        return phone;
    }

    /**
     * Returns the user's role as a Spring Security authority list.
     *
     * @return list containing a single {@link GrantedAuthority} for the user's role.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Returns the user's BCrypt password hash.
     *
     * @return the password hash string.
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the username — in this system the phone number is the username.
     *
     * @return the canonical phone number.
     */
    @Override
    public String getUsername() {
        return phone;
    }

    /**
     * Returns whether the account is non-expired.
     * Expiry is not used; active/inactive status is enforced separately.
     *
     * @return always {@code true}.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns whether the account is non-locked.
     *
     * @return always {@code true}.
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Returns whether the credentials are non-expired.
     *
     * @return always {@code true}.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Returns whether the account is active.
     *
     * @return {@code true} if the account is active.
     */
    @Override
    public boolean isEnabled() {
        return active;
    }
}
