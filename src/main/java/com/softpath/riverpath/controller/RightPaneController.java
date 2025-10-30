package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.pane.ZoomableScrollPane;
import com.softpath.riverpath.fileparser.CFDTriangleMesh;
import com.softpath.riverpath.model.Coordinates;
import com.softpath.riverpath.util.ColorObjectHandler;
import com.softpath.riverpath.util.DomainProperties;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@NoArgsConstructor
@Getter
@Setter
public class RightPaneController implements Initializable {

    private final ColorObjectHandler colorObjectHandler = new ColorObjectHandler();
    @FXML
    private VBox displayBox;
    @FXML
    private ToggleButton meshView;
    @FXML
    private ToggleButton simpleView;
    @FXML
    private ZoomableScrollPane meshPane;
    @FXML
    private MeshDisplayController meshPaneController;
    @FXML
    private SplitPane rightPane;
    @FXML
    private ConsolePaneController consolePaneController;
    @FXML
    private MainController mainController;
    // Declare the ToggleGroup
    private ToggleGroup displayToggleGroup;
    private MeshView domainMeshView;
    private Map<String, CFDTriangleMesh> allMeshes = new HashMap<>();
    private Map<String, Group> normalArrows = new HashMap<>();
    private Map<String, Shape> shapes = new HashMap<>();
    private Pane rootPane;
    private StackPane contentPane;

    /**
     * This method should be called when the domain mesh is loaded
     *
     * @param domainMesh the domain mesh
     */
    public void initiateDomain(CFDTriangleMesh domainMesh) {
        // Determining and saving the dimension
        domainMeshView = new MeshView(domainMesh);
        domainMeshView.setDrawMode(DrawMode.LINE);
        allMeshes.clear();
        // compute domain properties
        DomainProperties.getInstance().computeDomainProperties(meshPane, domainMeshView);
        DomainProperties.getInstance().set3D(domainMesh.is3D());
        // apply scale to the domain mesh
        applyScale(domainMeshView);
        // initiate fields
        rootPane = new Pane();
        contentPane = new StackPane();
        contentPane.prefWidthProperty().bind(rootPane.widthProperty());
        contentPane.prefHeightProperty().bind(rootPane.heightProperty());
        rootPane.getChildren().add(contentPane);

        //  Display the mesh in the 3D view
        Group mainGroup = new Group();
        mainGroup.getChildren().add(domainMeshView);
        rootPane.getChildren().add(mainGroup);

        meshPaneController.applyPaneView(rootPane);
        simpleView.setVisible(true);
        meshView.setVisible(true);
    }

    /**
     * Display only the borderlines with specified color
     */
    public void displayBorderlines() {
        // Simple check - if not initialized, do nothing
        if (domainMeshView == null || rootPane == null) {
            return;
        }
        // Utiliser la même structure que displayMesh()
        rootPane.getChildren().clear();
        // Create the domain border visualization (domain outline in black)
        CFDTriangleMesh domainAsMesh = (CFDTriangleMesh) domainMeshView.getMesh();
        double scaleFactor = DomainProperties.getInstance().getScaleFactor();
        List<Node> domainLines = domainAsMesh.createColoredBorderLines(scaleFactor, Color.BLACK);
        // Create a group to hold all visual elements
        Group linesGroup = new Group();
        linesGroup.getChildren().addAll(domainLines);
        // Iterate over all immersed meshes
        for (CFDTriangleMesh objectMesh : allMeshes.values()) {
            Color objectColor = objectMesh.getColor();
            List<Node> borderLines = objectMesh.createColoredBorderLines(scaleFactor, objectColor);
            //Add each object's borders immediately
            linesGroup.getChildren().addAll(borderLines);
        }
        addAllShapes(linesGroup);
        linesGroup.getChildren().addAll(normalArrows.values());
        rootPane.getChildren().add(linesGroup);
    }

    /**
     * Display domain and all objects
     */
    public void displayMesh() {
        // Simple check - if not initialized, do nothing
        if (domainMeshView == null || rootPane == null) {
            return;
        }

        // clear all
        rootPane.getChildren().clear();
        domainMeshView.setDrawMode(DrawMode.LINE);
        rootPane.getChildren().add(domainMeshView);
        Group meshGroup = new Group();
        // add immersed objects to the meshGroup
        addImmersedObjects(meshGroup);
        // add shapes to the meshGroup
        meshGroup.getChildren().addAll(shapes.values());
        // add normal arrows for half planes
        meshGroup.getChildren().addAll(normalArrows.values());
        rootPane.getChildren().add(meshGroup);
    }

    /**
     * This method should be called when an object mesh is loaded
     * It merges the domain mesh with the object mesh
     * and displays the result with two different colors
     *
     * @param controllerID the controller ID handling the immersed object
     * @param objectMesh   the object mesh
     */
    private void addObject(String controllerID, CFDTriangleMesh objectMesh) {
        objectMesh.setScale(DomainProperties.getInstance().getScaleFactor());
        objectMesh.setColor(colorObjectHandler.getNextColor());
        allMeshes.put(controllerID, objectMesh);
    }

    /**
     * Add and display the boundary to the right pane
     *
     * @param boundaryDefinitionController the boundary definition controller
     */
    public void addAndDisplay(BoundaryDefinitionController boundaryDefinitionController) {
        // Remove from both collections to ensure no duplicates
        allMeshes.remove(boundaryDefinitionController.toString());
        shapes.remove(boundaryDefinitionController.toString());

        // if standard shape then add it to shape list in the right pane
        if (boundaryDefinitionController.isStandardShape()) {
            addShape(boundaryDefinitionController);
        } else {
            ImmersedBoundaryController immersedController = boundaryDefinitionController.getImmersedBoundaryController();
            addObject(boundaryDefinitionController.toString(), immersedController.getImmersedObjectMesh());
        }

        if (simpleView.isSelected()) {
            displayBorderlines();  // Stay in simple mode if it was selected
        } else {
            displayMesh();         // Otherwise display in mesh mode
        }
    }

    /**
     * This method should be called when a boundary is removed
     *
     * @param boundaryDefinitionController the boundary definition controller
     */
    public void removeAndDisplay(BoundaryDefinitionController boundaryDefinitionController) {
        String id = boundaryDefinitionController.toString();
        if (boundaryDefinitionController.isImmersedObject()) {
            allMeshes.remove(id);
        } else {
            shapes.remove(boundaryDefinitionController.toString());
            // Remove the normal arrow if it exists
            normalArrows.remove(boundaryDefinitionController.toString());
        }
        // refresh the display
        displayMesh();
    }

    /**
     * Add a shape to the right pane
     *
     * @param boundaryDefinitionController the boundary definition controller
     */
    private void addShape(BoundaryDefinitionController boundaryDefinitionController) {
        if (boundaryDefinitionController.isStandardShape()) {
            Coordinates origin = new Coordinates(boundaryDefinitionController.getOriginX().getText(), boundaryDefinitionController.getOriginY().getText(), boundaryDefinitionController.getOriginZ().getText());
            Shape shape = boundaryDefinitionController.getBaseBoundaryController().getShape(DomainProperties.getInstance(), origin);
            shapes.put(boundaryDefinitionController.toString(), shape);
            // add normal to right pane
            normalArrows.put(boundaryDefinitionController.toString(), boundaryDefinitionController.getHalfPlaneBoundaryController().getPlanNormal(origin));
        }
    }

    /**
     * Add shapes to the group
     *
     * @param group the group to add the shapes to
     */
    private void addAllShapes(Group group) {
        if (!shapes.isEmpty()) {
            Collection<Shape> shapesCollection = shapes.values();
            shapesCollection.forEach((shape) -> {
                shape.setFill(Color.TRANSPARENT);
                shape.setStroke(Color.RED);
                shape.setStrokeWidth(1);
            });
            group.getChildren().addAll(shapesCollection);
        }
    }

    /**
     * Add immersed objects to the meshGroup
     *
     * @param meshGroup the mesh group
     */
    private void addImmersedObjects(Group meshGroup) {
        // For each mesh, create a MeshView with its own color
        double scaleFactor = DomainProperties.getInstance().getScaleFactor();
        for (CFDTriangleMesh objectMesh : allMeshes.values()) {
            Color objectColor = objectMesh.getColor();
            List<Node> objectNodes;
            if (!objectMesh.is3D()) {
                // For 2D objects, use createColoredLines to apply coloring.
                objectNodes = objectMesh.createColoredLines(scaleFactor, objectColor);
            } else {
                // For 3D objects, keep the existing logic
                MeshView objectMeshView = new MeshView(objectMesh);
                objectMeshView.setDrawMode(DrawMode.LINE);
                // Apply the scale
                // ⚠️JAVAFX_INVERTED_AXIS_Y
                objectMeshView.getTransforms().add(new Scale(scaleFactor, -scaleFactor, scaleFactor));
                objectNodes = Collections.singletonList(objectMeshView);
            }
            // Add to main group
            meshGroup.getChildren().addAll(objectNodes);
        }
    }

    private void applyScale(MeshView meshView) {
        double scaleFactor = DomainProperties.getInstance().getScaleFactor();
        // ⚠️JAVAFX_INVERTED_AXIS_Y
        Scale scale = new Scale(scaleFactor, -scaleFactor, scaleFactor);
        meshView.getTransforms().add(scale);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initialize the ToggleGroup
        displayToggleGroup = new ToggleGroup();

        // Add the ToggleButtons to the ToggleGroup
        simpleView.setToggleGroup(displayToggleGroup);
        meshView.setToggleGroup(displayToggleGroup);

        // Prevent buttons from being deselected
        displayToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                displayToggleGroup.selectToggle(oldVal);
            }
        });
        // Handle toggle between 2D and 3D mode
        meshView.setOnAction(event -> {
            if (meshView.isSelected()) {
                displayMesh();
            }
        });
        simpleView.setOnAction(event -> {
            if (simpleView.isSelected()) {
                displayBorderlines();
            }
        });
    }
}