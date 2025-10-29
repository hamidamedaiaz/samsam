package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.event.EventManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URL;
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
}