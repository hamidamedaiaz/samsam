package com.softpath.riverpath;

import com.softpath.riverpath.util.LicenseManager;
import com.softpath.riverpath.util.LicenseStateCallback;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;
import java.util.Optional;

/**
 * @author rhajou
 */
public class MainApplication extends Application {

    private LicenseManager licenseManager;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

        // Vérification licence
        licenseManager = LicenseManager.getInstance();

        licenseManager.setStateCallback(new LicenseStateCallback() {
            @Override
            public void onLicenseInvalidated(String reason) {
                // Licence invalidée pendant l'exécution (révocation, expiration, etc.)
                showError("Licence invalide : " + reason + "\nL'application va se fermer.");
                Platform.exit();
            }

            @Override
            public void onGracePeriodExceeded() {
                showWarning("Connexion Internet requise",
                        "Vous êtes hors ligne depuis trop longtemps.\n" +
                        "Veuillez vous reconnecter à Internet pour renouveler votre licence.\n\n" +
                        "L'application continuera de fonctionner jusqu'à la prochaine vérification.");
            }
        });
        if (!licenseManager.initialize()) {
            // Pas de licence valide - demander activation
            String licenseKey = showLicenseDialog();
            if (licenseKey == null || !licenseManager.activateLicense(licenseKey)) {
                showError("Une licence valide est requise pour utiliser CimCFD.\nL'application va se fermer.");
                Platform.exit();
                return;
            }
        }


        AnchorPane root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/com/softpath/riverpath/controller/welcome-page.fxml")));
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.UNDECORATED);


        primaryStage.setOnCloseRequest(event -> {
            if (licenseManager != null) {
                licenseManager.shutdown();
            }
        });

        primaryStage.show();
    }


    private String showLicenseDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Activation de licence - CimCFD");
        dialog.setHeaderText("Entrez votre clé de licence");
        dialog.setContentText("Clé de licence:");

        // Style du dialogue
        dialog.getDialogPane().setPrefWidth(400);

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur de licence - CimCFD");
        alert.setHeaderText("Licence requise");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText("Attention");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        if (licenseManager != null) {
            licenseManager.shutdown();
        }
        super.stop();
    }
}