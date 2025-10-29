package com.softpath.riverpath.controller;

import com.softpath.riverpath.model.Boundary;
import com.softpath.riverpath.model.Coordinates;
import com.softpath.riverpath.model.HalfPlaneBoundary;
import com.softpath.riverpath.util.DomainProperties;
import com.softpath.riverpath.util.ValidatedField;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.shape.Shape;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * HalfPlane3DBoundaryController
 *
 * @author rahajou
 */
@NoArgsConstructor
public class HalfPlane3DBoundaryController extends HalfPlaneBoundaryController {
    @FXML
    @ValidatedField
    @Getter
    private TextField normalZ;


    /**
     * @see BaseBoundaryController#importValues(Boundary)
     */
    @Override
    void importValues(Boundary boundary) {
        super.importValues(boundary);
        if (boundary instanceof HalfPlaneBoundary halfPlaneBoundary) {
            normalZ.setText(halfPlaneBoundary.getNormal().getZ());
        }
    }

    /**
     * @see BaseBoundaryController#getShape(DomainProperties, Coordinates)
     */
    @Override
    public Shape getShape(DomainProperties domainProperties, Coordinates origin) {
        // TODO : handle 3D plane
        return null;
    }

}
