package com.yr.perftest.platform.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.EnumSet;

@Entity
@Table(name = "user_accounts")
public class PersistentUserAccountRecord {
    @Id
    @Column(length = 80)
    private String username;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SystemRole role;

    protected PersistentUserAccountRecord() {
    }

    public PersistentUserAccountRecord(
            String username,
            String password,
            String displayName,
            boolean enabled,
            SystemRole role
    ) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.enabled = enabled;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    boolean passwordMatches(String rawPassword) {
        return password.equals(rawPassword);
    }

    boolean isEnabled() {
        return enabled;
    }

    AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(username, displayName, EnumSet.of(role));
    }
}
