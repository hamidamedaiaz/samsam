package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.event.CustomEvent;
import com.softpath.riverpath.custom.event.EventEnum;
import com.softpath.riverpath.custom.event.EventManager;
import com.softpath.riverpath.custom.pane.BoundaryTitledPane;
import com.softpath.riverpath.model.Simulation;
import com.softpath.riverpath.util.UtilityClass;
import com.softpath.riverpath.util.ValidatedField;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;


@NoArgsConstructor
@Getter
public class MeshingParametersController extends ValidAndCancelController {

    @FXML
    private BoundaryTitledPane titledPane;

    @FXML
    @ValidatedField
    private TextField nbElements;
    @FXML
    @ValidatedField
    private TextField hMin;
    @FXML
    @ValidatedField
    private TextField lMax;
    @FXML
    @ValidatedField
    private TextField nScaling;
    @FXML
    @ValidatedField
    private TextField scaleNorme;
    @FXML
    @ValidatedField
    private TextField err1;
    @FXML
    @ValidatedField
    private TextField err2;
    @FXML
    @ValidatedField
    private TextField adaptateur;
    @FXML
    @ValidatedField
    private TextField lMin;

    @FXML
    private void handleKeyReleased(KeyEvent keyEvent) {
        TextField source = (TextField) keyEvent.getSource();
        UtilityClass.handleTextWithDigitOnly(keyEvent);
        UtilityClass.checkNotBlank(source);
    }

    @Override
    protected Parent getRoot() {
        return this.titledPane;
    }

    @Override
    protected boolean customValidate() {
        EventManager.fireCustomEvent(new CustomEvent(EventEnum.MESHING_PARAMETERS_VALID));
        return true;
    }

    @Override
    protected void customReload() {
        // do nothing
    }

    public void importData(Simulation simulation) {
        // importer les données du fichier simulation
        nbElements.setText(simulation.getNbElements());
        hMin.setText(simulation.getHMin());
        lMax.setText(simulation.getLMax());
        nScaling.setText(simulation.getNScaling());
        scaleNorme.setText(simulation.getScaleNorme());
        err1.setText(simulation.getErr1());
        err2.setText(simulation.getErr2());
        adaptateur.setText(simulation.getAdaptateur());
        lMin.setText(simulation.getLMin());

        // validate imported data
        handleValidate(null);
    }

}