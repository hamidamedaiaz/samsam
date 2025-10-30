package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.event.EventManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

import static com.softpath.riverpath.custom.event.EventEnum.CONVERT_PYTHON_PROCESS_MESSAGE;

/**
 * This class to handle new project feature
 *
 * @author rhajou
 */
@NoArgsConstructor
@Getter
@Setter
public class MainController implements Initializable {

    @FXML
    private RightPaneController rightPaneController;

    @FXML
    private ProjectSetupController projectSetupController;

    @FXML
    private MenuBarController menuBarController;

    @FXML
    private BottomMainController bottomMainController;

    private Stage stage;

    public void displayMessageConsoleOutput(String message) {
        rightPaneController.getConsolePaneController().displayMessage(message);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        bottomMainController.setMainController(this);
        projectSetupController.setMainController(this);
        rightPaneController.setMainController(this);
        // Add event handler for converting python process message
        EventManager.addEventHandler(CONVERT_PYTHON_PROCESS_MESSAGE,
                event -> displayMessageConsoleOutput(event.getMessage()));
    }

    public void initializeStage(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (menuBarController != null) {
            menuBarController.setStage(stage);
            menuBarController.setMainController(this); // Add this line
        }
    }

    public String promptErrorOnValidation() {
        // Création de l'alerte
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("ERREUR DE VALIDATION DE VALEURS");
        alert.setHeaderText("La validation a échoué. Voulez-vous retourner à votre session?");
        alert.setContentText("Choisissez une option :");

        // Personnalisation des boutons
        ButtonType retToUi = new ButtonType("YES");
        ButtonType closeAnywayBtn = new ButtonType("NO");
        ButtonType cancelBtn = new ButtonType("CANCEL", ButtonType.CANCEL.getButtonData());

        alert.getButtonTypes().setAll(retToUi, closeAnywayBtn, cancelBtn);

        // Dialogue style
        alert.getDialogPane().setPrefWidth(400);

        // Affichage bloquant
        Optional<ButtonType> result = alert.showAndWait();

        // Retourner un String correspondant au bouton cliqué
        if (result.isPresent()) {
            if (result.get() == retToUi) return "RETURN_TO_UI";
            else if (result.get() == closeAnywayBtn) return "CLOSE_ANYWAY";
            else return "CANCEL";
        }
        return "cancel"; // valeur par défaut si la fenêtre est fermée autrement
    }

    public String showActionsOnExitWithStableState() {
        // Création de l'alerte
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Sauvegarder");
        alert.setHeaderText("Voulez-vous sauvegarder la session en cours ?");
        alert.setContentText("Choisissez une option :");

        // Personnalisation des boutons
        ButtonType saveBtn = new ButtonType("Save");
        ButtonType closeAnywayBtn = new ButtonType("Close Anyway");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());

        alert.getButtonTypes().setAll(saveBtn, closeAnywayBtn, cancelBtn);

        // Dialogue style
        alert.getDialogPane().setPrefWidth(400);

        // Affichage bloquant
        Optional<ButtonType> result = alert.showAndWait();

        // Retourner un String correspondant au bouton cliqué
        if (result.isPresent()) {
            if (result.get() == saveBtn) return "SAVE";
            else if (result.get() == closeAnywayBtn) return "CLOSE_ANYWAY";
            else return "CANCEL";
        }
        return "CANCEL"; // valeur par défaut si la fenêtre est fermée autrement
    }
}