package com.ecommerce.modules.auth.entity;

/**
 * Lifecycle status of customer and administrative user accounts.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED,
    SUSPENDED,
    PENDING_VERIFICATION
}

