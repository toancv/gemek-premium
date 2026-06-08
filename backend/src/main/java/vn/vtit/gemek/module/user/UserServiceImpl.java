/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.user;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.audit.Auditable;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.util.PhoneUtils;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.dto.ResetPasswordRequest;
import vn.vtit.gemek.module.user.dto.UpdateUserRequest;
import vn.vtit.gemek.module.user.dto.UserDetailResponse;
import vn.vtit.gemek.module.user.dto.UserResponse;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.mapper.UserMapper;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.UUID;

/**
 * Implementation of {@link UserService} for user management operations.
 *
 * <p>All mutating methods are transactional. Passwords are never logged.
 */
@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Constructs the service with its required dependencies.
     *
     * @param userRepository  the user JPA repository.
     * @param passwordEncoder the BCrypt password encoder.
     * @param userMapper      the MapStruct user mapper.
     */
    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<UserResponse> listUsers(UserRole role, Boolean isActive, String search, Pageable pageable) {
        log.debug("Listing users — role={}, isActive={}, search={}", role, isActive, search);
        Specification<User> spec = (root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("active"), isActive));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<UserResponse> page = userRepository.findAll(spec, pageable).map(userMapper::toUserResponse);
        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "User")
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedPhone = PhoneUtils.normalize(request.phone());
        log.debug("Creating user with phone={}", normalizedPhone);

        // Phone uniqueness check — primary login identifier.
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS,
                    "Phone number is already registered: " + normalizedPhone);
        }

        // Email uniqueness check — informational, optional, but still unique-nullable.
        String email = (request.email() != null && !request.email().isBlank()) ? request.email() : null;
        if (email != null && userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email address is already registered: " + email);
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setPhone(normalizedPhone);
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);

        User saved = userRepository.save(user);
        log.info("User created — id={}, role={}", saved.getId(), saved.getRole());
        return userMapper.toUserResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserDetailResponse getUserById(UUID id) {
        User user = findOrThrow(id);
        return userMapper.toUserDetailResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "User")
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.debug("Updating user id={}", id);
        User user = findOrThrow(id);
        // SEC-04: log role changes at WARN so they are visible in any log aggregator.
        if (!user.getRole().equals(request.role())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actorId = auth != null ? auth.getName() : "unknown";
            log.warn("Role change — userId={} oldRole={} newRole={} actorId={}",
                    id, user.getRole(), request.role(), actorId);
        }
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(request.isActive());
        User saved = userRepository.save(user);
        log.info("User updated — id={}", saved.getId());
        return userMapper.toUserResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Auditable(action = "DELETE", entityType = "User")
    public void deactivateUser(UUID id, UUID requestUserId) {
        log.debug("Deactivating user id={} requested by {}", id, requestUserId);

        // Prevent admin from deactivating their own account.
        if (id.equals(requestUserId)) {
            throw new AppException(ErrorCode.SELF_OPERATION_NOT_ALLOWED,
                    "Cannot deactivate your own account.");
        }

        User user = findOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated — id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Auditable(action = "RESET_PASSWORD", entityType = "User")
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        log.debug("Resetting password for user id={}", id);
        User user = findOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Password reset for user id={}", id);
    }

    /**
     * Loads a user by UUID or throws a NOT_FOUND exception.
     *
     * @param id the user UUID.
     * @return the found {@link User} entity.
     * @throws AppException with {@link ErrorCode#NOT_FOUND} if the user does not exist.
     */
    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found: " + id));
    }
}
