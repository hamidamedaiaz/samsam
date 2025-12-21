package com.softpath.riverpath.util;

import com.cryptlex.lexactivator.LexActivator;
import com.cryptlex.lexactivator.LexActivatorException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestionnaire de licence Cryptlex pour CimCFD
 *
 * @author rhajou
 */
@Slf4j
public class LicenseManager {

    private static final String PRODUCT_DATA = "RTIxQzUzNzg4QTk3QkI0OTI4OEMzNzBCNjgyQjhFNzQ=.esSsCynB+bOhRp5HzmMZfkjTOkzzon2aI3sZSzrPH+kACwTUtusZUAcA7NYVtYc4VascxZwrB4gqcEyRytkVuzC6e17+3yPwr+7YV8lvQjeDDol82Q6cHKjpAf9+Y4hDSs9+MWVvvbHmRCwgZOjhW3FAYam5tgPbUKRYOrUak/BfOEkGZUYAKbDHK0Jra0+zHHZ+jX4gwiSvoxPTYJ1E/drN+5SWACrIJZHvMtQBGh9zsBrhOXX2x9/oxiHPShjjAXwvgxVLI+m8RNbeYpPxaLKR69DIux4Ppcs3N5tFN+shilg9LnKAHsWxU/28NHlKsghxZIncqnypiMWJRhoV3XGcMe/LgB6TIJ8TtWYotzjx6Uy/CqJVLlYRyKmna6N5naMTXTFjL7YA/K/3aqqtHfpW1ZVZ4cn5kKRVhiRpodE9ZxwDqfftB14fdH+COURQ4JrXtY/sJpsJ39EHO09LEeK2cqauSi7gaE55agfK0cH56G20jN37TcZRkDJHhRVzw38Gf+6ZeElH7XJ3pJR+XHEM8bkmaOVdGBjvsyq26MxKnZjgt8mbkQfQ611AaQqZVPktu5qeNENgZ4C7LVd3hVCVVRuGn/uCnb1QncdgZbOaOUHdPT2T5JtAMS542QCtWOYYJacpphCJfhZuY0fOKdhAd0Kz1N3sZKS3oAHWA9MXQIVeagLyUQXyViLrbEQpO/1wxnfyjRYq9/9vaANuBCElZRlr3E+8Y+/BBh9yOqX2/UAOJArUVT1A4c/MFDHvU+LcrRSmEKnb0tfp0A4ulCqSeKrV+Dt0tSvGIZXyGlU0a9XflQ9ey+bm9CJtuFoY";
    private static final String PRODUCT_ID = "01979ce0-47aa-7a08-82ce-f93bed398e0f";


    private static final int LA_OK = 0;
    private static final int LA_USER = 1;
    private static final int LA_EXPIRED = 20;
    private static final int LA_SUSPENDED = 21;
    private static final int LA_GRACE_PERIOD_OVER = 22;
    private static final int LA_TRIAL_EXPIRED = 25;
    private static final int LA_E_LICENSE_KEY = 42;
    private static final int LA_E_INET = 43;
    private static final int LA_E_TIME_MODIFIED = 45;
    private static final int LA_E_LICENSE_EXPIRED = 47;
    private static final int LA_E_ACTIVATION_LIMIT = 49;
    private static final int LA_E_ACTIVATION_NOT_FOUND = 50;
    private static final int LA_E_REVOKED = 52;

    private static final int CHECK_INTERVAL_MINUTES = 1;
    private static final Object lock = new Object();
    private static volatile LicenseManager instance;
    private final AtomicBoolean isValid = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> periodicCheckTask;


    /**
     * Callback interface for notifying the UI about license state changes
     */

    private volatile LicenseStateCallback stateCallback;


    private LicenseManager() {

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Cryptlex-LicenseChecker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    public static LicenseManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LicenseManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialise et vérifie la licence existante
     *
     * @return true si une licence valide existe
     */
    public boolean initialize() {
        if (isInitialized.get()) {
            return isValid.get();
        }

        try {
            LexActivator.SetProductData(PRODUCT_DATA);
            LexActivator.SetProductId(PRODUCT_ID, LA_USER);


            int result = LexActivator.IsLicenseGenuine();


            return handleLicenseStatus(result, true);

        } catch (LexActivatorException e) {
            log.error("Critical error during license initialization: {} (code: {})",
                    e.getMessage(), e.getCode());
            isInitialized.set(true);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during license initialization", e);
            isInitialized.set(true);
            return false;
        }
    }

    /**
     * Active une nouvelle licence
     *
     * @param licenseKey la clé de licence
     * @return true si l'activation réussit
     */
    public boolean activateLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            log.error("License key is null or empty");
            return false;
        }

        try {
            // Configure SDK
            LexActivator.SetProductData(PRODUCT_DATA);
            LexActivator.SetProductId(PRODUCT_ID, LA_USER);
            LexActivator.SetLicenseKey(licenseKey.trim());

            // Attempt activation (requires internet)
            log.debug("Calling ActivateLicense()...");
            int activationResult = LexActivator.ActivateLicense();

            if (activationResult != LA_OK) {
                log.error("Activation failed: {} (code: {})",
                        getErrorMessage(activationResult), activationResult);
                return false;
            }

            // Immediately verify the activation
            int verificationResult = LexActivator.IsLicenseGenuine();

            if (verificationResult == LA_OK) {
                log.info("✓ License activated and verified successfully");
                isValid.set(true);
                isInitialized.set(true);
                startPeriodicVerification();
                return true;
            } else {
                log.error("Activation succeeded but verification failed: {} (code: {})",
                        getErrorMessage(verificationResult), verificationResult);
                isValid.set(false);
                return false;
            }

        } catch (LexActivatorException e) {
            log.error("License activation exception: {} (code: {})",
                    e.getMessage(), e.getCode());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during license activation", e);
            return false;
        }
    }

    /**
     * Sets the callback for license state changes.
     * The callback will be invoked on the JavaFX Application Thread.
     */
    public void setStateCallback(LicenseStateCallback callback) {
        this.stateCallback = callback;
    }

    /**
     * Shuts down the license manager and stops all background tasks.
     * <p>
     * Should be called when the application is closing.
     */
    public void shutdown() {
        log.info("Shutting down LicenseManager...");

        stopPeriodicVerification();

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("LicenseManager shutdown complete");
    }


    /**
     * Handles the license status code and updates internal state.
     */
    private boolean handleLicenseStatus(int statusCode, boolean isStartup) {
        switch (statusCode) {
            case LA_OK:
                // License is valid
                if (isStartup) {
                    log.info("✓ License is valid");
                    isValid.set(true);
                    isInitialized.set(true);
                    startPeriodicVerification();
                } else {
                    // Runtime check
                    isValid.set(true);
                    log.debug("License verification passed");
                }
                return true;

            case LA_EXPIRED:
            case LA_E_LICENSE_EXPIRED:
                // License has expired
                log.warn("✗ License has expired");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyInvalidation("License has expired");
                }
                return false;

            case LA_SUSPENDED:
                // License suspended by administrator
                log.warn("✗ License has been suspended");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyInvalidation("License has been suspended");
                }
                return false;

            case LA_E_REVOKED:
                // License has been revoked
                log.warn("✗ License has been revoked");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyInvalidation("License has been revoked");
                }
                return false;

            case LA_GRACE_PERIOD_OVER:
                // Offline grace period has expired - user must connect to internet
                log.warn("✗ Offline grace period exceeded - internet connection required");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyGracePeriodExceeded();
                }
                return false;

            case LA_TRIAL_EXPIRED:
                // Trial period has ended
                log.warn("✗ Trial period has expired");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                }
                return false;

            case LA_E_ACTIVATION_NOT_FOUND:
                // No activation found on this machine
                log.info("No license activation found on this machine");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                }
                return false;

            case LA_E_TIME_MODIFIED:
                // System clock has been tampered with
                log.error("✗ System clock has been modified - potential tampering detected");
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyInvalidation("System clock tampering detected");
                }
                return false;

            case LA_E_INET:
                // Network error during sync attempt
                // This is NOT a critical error during startup if cache exists
                log.debug("Network error during sync (will use cache if available)");
                // Don't change isValid state - previous state remains
                return isValid.get();

            case LA_USER:

                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    notifyInvalidation("License is invalid or has been revoked ");
                }
                return false;

            default:
                // Unexpected status code - treat as error
                log.error(" Unexpected license status code: {} - {}",
                        statusCode, getErrorMessage(statusCode));
                isValid.set(false);
                if (isStartup) {
                    isInitialized.set(true);
                } else {
                    log.error("Calling callback for unexpected code");
                    notifyInvalidation("License validation failed (Code: " + statusCode + ")");
                }
                return false;
        }
    }

    /**
     * Starts periodic background license verification.
     */
    private void startPeriodicVerification() {
        if (periodicCheckTask != null && !periodicCheckTask.isDone()) {
            log.debug("Periodic verification already running");
            return;
        }

        log.info("Starting periodic license verification (every {} minutes)", CHECK_INTERVAL_MINUTES);

        periodicCheckTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("Running periodic license check...");

                int result = LexActivator.IsLicenseGenuine();

                // Handle the result
                // handleLicenseStatus will update state and invoke callbacks if needed
                handleLicenseStatus(result, false);

            } catch (LexActivatorException e) {
                // Exception during check (e.g., offline and cache expired)
                log.warn("Periodic license check failed: {} (code: {})",
                        e.getMessage(), e.getCode());

                // If it's a critical error, update state
                if (e.getCode() == LA_GRACE_PERIOD_OVER) {
                    isValid.set(false);
                    notifyGracePeriodExceeded();
                } else if (e.getCode() != LA_E_INET) {
                    // Network errors are acceptable, other errors are not
                    isValid.set(false);
                    notifyInvalidation(e.getMessage());
                }
            } catch (Exception e) {
                log.error("Unexpected error during periodic license check", e);
            }

        }, CHECK_INTERVAL_MINUTES, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Stops the periodic verification task.
     */
    private void stopPeriodicVerification() {
        if (periodicCheckTask != null && !periodicCheckTask.isDone()) {
            log.debug("Stopping periodic license verification");
            periodicCheckTask.cancel(false);
            periodicCheckTask = null;
        }
    }

    /**
     * Notifies the callback that the license has been invalidated.
     * Runs on JavaFX Application Thread if JavaFX is available.
     */
    private void notifyInvalidation(String reason) {

        if (stateCallback != null) {
            try {
                // Check if JavaFX is available

                javafx.application.Platform.runLater(() -> {

                    stateCallback.onLicenseInvalidated(reason);
                });
            } catch (Exception e) {
                // JavaFX not available or not initialized
                log.warn("Could not notify UI (JavaFX not available): {}", reason);
                stateCallback.onLicenseInvalidated(reason);
            }
        } else {
            log.error(" NO CALLBACK REGISTERED! License invalidated but cannot notify: {}", reason);
        }
    }

    /**
     * Notifies the callback that the grace period has been exceeded.
     * Runs on JavaFX Application Thread if JavaFX is available.
     */
    private void notifyGracePeriodExceeded() {
        if (stateCallback != null) {
            try {
                javafx.application.Platform.runLater(() -> {
                    stateCallback.onGracePeriodExceeded();
                });
            } catch (Exception e) {
                log.warn("Could not notify UI (JavaFX not available): grace period exceeded");
                stateCallback.onGracePeriodExceeded();
            }
        } else {
            log.warn("Grace period exceeded but no callback registered");
        }
    }

    /**
     * Converts Cryptlex status codes to user-friendly messages.
     */
    private String getErrorMessage(int code) {
        return switch (code) {
            case LA_OK -> "Succès";
            case LA_EXPIRED -> "Licence expirée";
            case LA_SUSPENDED -> "Licence suspendue par l'administrateur";
            case LA_GRACE_PERIOD_OVER -> "Période hors ligne dépassée - connexion Internet requise";
            case LA_TRIAL_EXPIRED -> "Période d'essai expirée";
            case LA_E_LICENSE_KEY -> "Format de clé de licence invalide";
            case LA_E_INET -> "Erreur réseau lors de la synchronisation";
            case LA_E_TIME_MODIFIED -> "Horloge système modifiée - falsification détectée";
            case LA_E_LICENSE_EXPIRED -> "Licence expirée";
            case LA_E_ACTIVATION_LIMIT -> "Limite d'activation atteinte";
            case LA_E_ACTIVATION_NOT_FOUND -> "Aucune activation trouvée sur cette machine";
            case LA_E_REVOKED -> "Licence révoquée";
            default -> "Erreur inconnue (code: " + code + ")";
        };
    }
}