package com.mint.auth.dto;

import com.mint.auth.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe public representation of a User.
 * Excludes sensitive fields like password hash.
 */
public class UserResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String role;
    private Instant createdAt;

    public UserResponse() {
    }

    public UserResponse(UUID id, String email, String fullName, String role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
