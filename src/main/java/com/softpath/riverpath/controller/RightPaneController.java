package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.pane.ZoomableScrollPane;
import com.softpath.riverpath.fileparser.CFDTriangleMesh;
import com.softpath.riverpath.model.Coordinates;
import com.softpath.riverpath.util.DisplayMode;
import com.softpath.riverpath.util.DomainProperties;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.net.URL;
import java.util.ResourceBundle;

@NoArgsConstructor
@Getter
@Setter
public class RightPaneController implements Initializable {
    private final MeshObjectManager objectManager = new MeshObjectManager();
    private final SceneRenderer sceneRenderer = new SceneRenderer(objectManager);
    private final GlobalContextMenuBuilder menuBuilder = new GlobalContextMenuBuilder(objectManager, sceneRenderer);
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
    private ToggleGroup displayToggleGroup;
    private MeshView domainMeshView;
    private Pane rootPane;
    private StackPane contentPane;
    private ContextMenu globalContextMenu;

    /**
     * Initialize the domain mesh and setup the scene
     */
    public void initiateDomain(CFDTriangleMesh domainMesh) {
        // Create and configure domain mesh view
        domainMeshView = new MeshView(domainMesh);
        domainMeshView.setDrawMode(DrawMode.LINE);

        // Clear all objects
        objectManager.clear();

        // Compute domain properties
        DomainProperties.getInstance().computeDomainProperties(meshPane, domainMeshView);
        DomainProperties.getInstance().set3D(domainMesh.is3D());

        // Apply scale to domain
        sceneRenderer.applyScale(domainMeshView);
        sceneRenderer.setDomainMeshView(domainMeshView);

        // Initialize root pane
        rootPane = new Pane();
        contentPane = new StackPane();
        contentPane.prefWidthProperty().bind(rootPane.widthProperty());
        contentPane.prefHeightProperty().bind(rootPane.heightProperty());
        rootPane.getChildren().add(contentPane);

        // Initial display
        sceneRenderer.renderScene(rootPane);

        // Apply pane view
        meshPaneController.applyPaneView(rootPane);

        // Setup global context menu (only way to change display modes)
        setupGlobalContextMenu();
    }

    /**
     * Display/refresh the complete scene
     * Delegates to SceneRenderer
     */
    public void displayBorderlines() {
        sceneRenderer.renderScene(rootPane);
    }

    /**
     * Update domain display mode
     */
    public void applySelectedDisplayMode() {
        // Update domain display mode based on selected toggle button
        if (simpleView.isSelected()) {
            sceneRenderer.setDomainDisplayMode(DisplayMode.SIMPLE);
        } else {
            sceneRenderer.setDomainDisplayMode(DisplayMode.MESH);
        }
        // Refresh the display
        displayBorderlines();
    }

    /**
     * Add and display the boundary to the right pane
     * Delegates to objectManager
     */
    public void addAndDisplay(BoundaryDefinitionController boundaryDefinitionController) {
        String controllerId = boundaryDefinitionController.toString();
        Color existingColor = objectManager.getExistingColor(controllerId);

        // Remove from both collections to ensure no duplicates
        objectManager.removeObject(controllerId);
        objectManager.removeShape(controllerId);

        // if standard shape then add it to shape list in the right pane
        if (boundaryDefinitionController.isStandardShape()) {
            addShape(boundaryDefinitionController);
        } else {
            ImmersedBoundaryController immersedController = boundaryDefinitionController.getImmersedBoundaryController();
            Coordinates origin = new Coordinates(
                    boundaryDefinitionController.getOriginX().getText(),
                    boundaryDefinitionController.getOriginY().getText(),
                    boundaryDefinitionController.getOriginZ().getText()
            );

            // Add object to manager
            objectManager.addObject(controllerId, immersedController.getImmersedObjectMesh(), origin, existingColor);

            // Store the display name
            String displayName = boundaryDefinitionController.getNameValue().getText();
            if (displayName != null && !displayName.trim().isEmpty()) {
                objectManager.setDisplayName(controllerId, displayName);
            } else {
                objectManager.setDisplayName(controllerId, immersedController.getImportObject().getText());
            }
        }

        // Refresh display
        displayBorderlines();
    }

    /**
     * Remove a boundary and refresh display
     * Delegates to objectManager

     */
    public void removeAndDisplay(BoundaryDefinitionController boundaryDefinitionController) {
        if (boundaryDefinitionController.isImmersedObject()) {
            objectManager.removeObject(boundaryDefinitionController.toString());
        } else {
            objectManager.removeShape(boundaryDefinitionController.toString());
            objectManager.removeNormalArrow(boundaryDefinitionController.toString());
        }

        // refresh the display
        applySelectedDisplayMode();
    }

    /**
     * Add a shape to the object manager
     */
    private void addShape(BoundaryDefinitionController boundaryDefinitionController) {
        if (boundaryDefinitionController.isStandardShape()) {
            Coordinates origin = new Coordinates(
                    boundaryDefinitionController.getOriginX().getText(),
                    boundaryDefinitionController.getOriginY().getText(),
                    boundaryDefinitionController.getOriginZ().getText()
            );

            Shape shape = boundaryDefinitionController.getBaseBoundaryController()
                    .getShape(DomainProperties.getInstance(), origin);
            objectManager.addShape(boundaryDefinitionController.toString(), shape);

            // add normal arrow
            objectManager.addNormalArrow(
                    boundaryDefinitionController.toString(),
                    boundaryDefinitionController.getHalfPlaneBoundaryController().getPlanNormal(origin)
            );
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        ////Initialization complete - display modes are now controlled via context menu only  : )
    }

    /**
     * Setup the global context menu that appears when right-clicking anywhere in the scene
     */
    private void setupGlobalContextMenu() {
        globalContextMenu = new ContextMenu();

        /// Attach right-click listener to rootPane
        rootPane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                menuBuilder.buildContextMenu(globalContextMenu, v -> displayBorderlines());
                globalContextMenu.show(rootPane, event.getScreenX(), event.getScreenY());
                event.consume();
            } else if (event.getButton() == MouseButton.PRIMARY) {
                // Close menu on left click
                globalContextMenu.hide();
            }
        });
    }
}