package com.softpath.riverpath.util;

public interface LicenseStateCallback {
    /**
     * Called when license becomes invalid during runtime
     * (e.g., expired, revoked, suspended)
     */
    void onLicenseInvalidated(String reason);

    /**
     * Called when offline grace period is exceeded
     * User should be prompted to connect to internet
     */
    void onGracePeriodExceeded();
}