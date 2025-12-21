


package com.softpath.riverpath;

import com.cryptlex.lexactivator.LexActivator;
import com.cryptlex.lexactivator.LexActivatorException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;

import java.io.UnsupportedEncodingException;

import static com.cryptlex.lexactivator.LexActivator.LA_OK;
import static com.cryptlex.lexactivator.LexActivator.LA_USER;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
class LicenseIntegrationTest {

    private static final String LICENSE_KEY = "F0AC5C-9AE250-4861A5-AA3F48-437E72-5FA0AF";
    private static final String PRODUCT_DATA = "RjJDNzEwNjZDRUZGQzZCRUYzNEZCOTdENzVGN0M0MUQ=.8yZVRspuHKFAm/Pt1629vTcW9ugfpVJKvaU9UHqt+rHapS8RWNDKbTYyjCxQEI/Kw5WIMlmHdJC1QWoimte4r9sTDS0wLMZjO+ClR7jjQJk0prITaPfuJCtgJ+8Gd9xgdVBOEvP38FtO3oVYRu4RSXNIRlKrPqL95YPbMIN+GfdNr0w7j5KtHz4sY8v/WFkzbS+tbvMQJBqoWyx73kEjjYUZyoRGlpKxYnv3ZDbIBJke97O7PbesKI57jRDK0qhLQWPpZTxRKrUh5y0CrJaB9EnkviLdvBtIuwMsammdpiblwN45VDcRT+LnP7BRWqe1kpocxh4Yl2ZzzIw6pJxF4gBd1kDtKaBrf5fFh6PjIfBxljlLtnXTiRx/I7B69/KQou3gguP8dLqMjORJnAtxKQQxWbi2LUT3tQiSlvMy9BixRguj8/OLx9U4qnMc2pMnuEkKuYVO8si01++fI/svJsrzSRWqrfdz15nl97QaaHwRZ9gxLQZQXfFJkx4z9Lk6W7yIxvOH6ZSlPUMbRYTOgjQDKVv7Ew07LIxwgGIZkATh9C+3D1pLbQxaluiLa/HOcqoe426vzOWK/VoG7lFLaAzLQOgnQQUOcKQff1ZgjQ3nie+hYPH9nIB/Z+mrxjt4w9Mb51Ap6tns0WlRDh9jBX5QWHziW60MxjiSvbVOcc90AM8mzZ1Go88iWR6oaK+IzRpb/f3OuS3eN5+rZa2GLdBVAHQ1H+ylO9G9j0VdMFq73gIn2tOYy/AWpYb4WRSxoHmRiE1vhGD677nFdA+BzwMzU3+rPgfY1vYhuLyZ9x1ukYurUwqGIk4lLEokPRoJ";
    private static final String PRODUCT_ID = "2ef24083-0930-409b-b5a6-d64e24bcafc5";
    private static final String METER_NAME = "cpu_cores";
    private static final int CORES_TO_USE = 70;

    @BeforeAll
    static void setUp() {
        System.out.println("Initialisation des tests LexActivator");
    }

    @AfterAll
    static void tearDown() {
        try {
            // S'assurer que les ressources sont correctement libérées après tous les tests
            LexActivator.DecrementActivationMeterAttributeUses(METER_NAME, CORES_TO_USE);
        } catch (Exception e) {
            System.err.println("Erreur lors du nettoyage final: " + e.getMessage());
        }
    }

    @Test
    @Order(1)
    void testSetProductData() throws LexActivatorException {
        // Test de l'appel 1: SetProductData
        LexActivator.SetProductData(PRODUCT_DATA);
        System.out.println("✓ Test 1: SetProductData réussi");
    }

    @Test
    @Order(2)
    void testSetProductId() throws LexActivatorException {
        // Test de l'appel 2: SetProductId
        LexActivator.SetProductId(PRODUCT_ID, LA_USER);
        System.out.println("✓ Test 2: SetProductId réussi");
    }

    @Test
    @Order(3)
    void testSetLicenseKey() throws LexActivatorException {
        // Test de l'appel 3: SetLicenseKey
        LexActivator.SetLicenseKey(LICENSE_KEY);
        System.out.println("✓ Test 3: SetLicenseKey réussi");
    }

    @Test
    @Order(4)
    void testActivateLicense() throws LexActivatorException {
        // Test de l'appel 4: ActivateLicense
        // Nécessite que les appels précédents aient été effectués
        testSetProductData();
        testSetProductId();
        testSetLicenseKey();

        int activationStatus = LexActivator.ActivateLicense();
        assertTrue(activationStatus == LA_OK, "ActivateLicense devrait retourner LA_OK");
        System.out.println("✓ Test 4: ActivateLicense réussi");
    }

    @Test
    @Order(5)
    void testIsLicenseGenuine() throws LexActivatorException {
        // Test de l'appel 5: IsLicenseGenuine
        // Nécessite que l'activation ait été effectuée
        testActivateLicense();

        int genuineStatus = LexActivator.IsLicenseGenuine();
        assertTrue(genuineStatus == LA_OK, "IsLicenseGenuine devrait retourner LA_OK");
        System.out.println("✓ Test 5: IsLicenseGenuine réussi");
    }

    @Test
    @Order(6)
    void testIncrementActivationMeterAttributeUses() throws LexActivatorException, UnsupportedEncodingException, InterruptedException {
        // Préparer la licence
        testActivateLicense();
        testIsLicenseGenuine();

        System.out.println("Début du test - " + CORES_TO_USE + " cores verrouillés. Utilisez Ctrl+C pour arrêter.");

        // Bloquer les cores et rester en attente(Permet d'aller checker le dashboard)
        LexActivator.IncrementActivationMeterAttributeUses(METER_NAME, CORES_TO_USE);

        // Attendre indéfiniment (jusqu'à interruption manuelle)
        while(true) {
            System.out.println("Cores toujours verrouillés: " + CORES_TO_USE + ". Test en cours...");
            Thread.sleep(30000); // Affiche un message toutes les 30 secondes
        }
    }

    @Test
    @Order(7)
    void testDecrementActivationMeterAttributeUses() throws LexActivatorException, UnsupportedEncodingException {
        // Test de l'appel 7: DecrementActivationMeterAttributeUses
        // S'assurer que le compteur a été incrémenté d'abord
        //Pas besoin normalement de cette fonction car cela se fait automatiquement
        //testIncrementActivationMeterAttributeUses();

        LexActivator.DecrementActivationMeterAttributeUses(METER_NAME, CORES_TO_USE);
        System.out.println("✓ Test 7: DecrementActivationMeterAttributeUses réussi");
    }
}