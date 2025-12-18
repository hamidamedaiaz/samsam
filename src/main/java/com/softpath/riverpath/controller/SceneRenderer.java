package com.softpath.riverpath.controller;

import com.softpath.riverpath.fileparser.CFDTriangleMesh;
import com.softpath.riverpath.util.DisplayMode;
import com.softpath.riverpath.util.DomainProperties;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
  Responsible for rendering the complete scene:
  Domain with SIMPLE or MESH mode
  Immersed objects with individual modes
  Shapes standard boundaries
 * Normal arrows
 */
public class SceneRenderer {

    private final MeshObjectManager objectManager;
    @Setter
    private MeshView domainMeshView;
    @Setter
    @Getter
    private DisplayMode domainDisplayMode = DisplayMode.MESH;

    public SceneRenderer(MeshObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    /**
     * Render the complete scene to the root pane
     */
    public void renderScene(Pane rootPane) {
        if (domainMeshView == null || rootPane == null) {
            return;
        }

        rootPane.getChildren().clear();
        double scaleFactor = DomainProperties.getInstance().getScaleFactor();
        Group mainGroup = new Group();

        // Display domain according to its mode
        renderDomain(mainGroup, scaleFactor);

        // Display objects according to their individual modes
        renderObjects(mainGroup, scaleFactor);

        // Add shapes and normal arrows
        renderShapes(mainGroup);
        mainGroup.getChildren().addAll(objectManager.getNormalArrows().values());

        rootPane.getChildren().add(mainGroup);
    }

    /**
     * Render the domain in SIMPLE or MESH mode
     */
    private void renderDomain(Group mainGroup, double scaleFactor) {
        CFDTriangleMesh domainAsMesh = (CFDTriangleMesh) domainMeshView.getMesh();

        if (domainDisplayMode == DisplayMode.SIMPLE) {
            // Domain in SIMPLE mode
            List<Node> domainLines = domainAsMesh.createColoredBorderLines(scaleFactor, Color.BLACK);
            mainGroup.getChildren().addAll(domainLines);
        } else {
            // Domain in MESH mode: show full mesh
            domainMeshView.setDrawMode(DrawMode.LINE);
            mainGroup.getChildren().add(domainMeshView);
        }
    }

    /**
     * Render all objects with their individual modes
     */
    private void renderObjects(Group mainGroup, double scaleFactor) {
        for (Map.Entry<String, CFDTriangleMesh> entry : objectManager.getAllMeshes().entrySet()) {
            String objectId = entry.getKey(); // Use the correct key (controllerId)
            CFDTriangleMesh objectMesh = entry.getValue();
            Color objectColor = objectMesh.getColor();
            DisplayMode objectMode = objectManager.getDisplayMode(objectId);

            List<Node> objectNodes = createNodesForMode(objectMesh, objectColor, scaleFactor, objectMode);
            Group objectGroup = new Group(objectNodes);
            objectGroup.setPickOnBounds(true);
            mainGroup.getChildren().add(objectGroup);
        }
    }

    /**
     * Render all shapes (standard boundaries)
     */
    private void renderShapes(Group mainGroup) {
        Map<String, Shape> shapes = objectManager.getShapes();
        if (!shapes.isEmpty()) {
            Collection<Shape> shapesCollection = shapes.values();
            shapesCollection.forEach(shape -> {
                shape.setFill(Color.TRANSPARENT);
                shape.setStroke(Color.RED);
                shape.setStrokeWidth(1);
            });
            mainGroup.getChildren().addAll(shapesCollection);
        }
    }

    /**
     * Create nodes for an object based on its display mode
     */
    private List<Node> createNodesForMode(CFDTriangleMesh objectMesh, Color objectColor, double scaleFactor, DisplayMode mode) {
        if (mode == DisplayMode.SIMPLE) {
            // Mode SIMPLE: show only borders
            return objectMesh.createColoredBorderLines(scaleFactor, objectColor);
        }

        if (!objectMesh.is3D()) {
            // For 2D objects, use createColoredLines
            return objectMesh.createColoredLines(scaleFactor, objectColor);
        } else {
            // For 3D objects, create MeshView
            MeshView objectMeshView = new MeshView(objectMesh);
            objectMeshView.setDrawMode(DrawMode.LINE);
            // Apply the scale (⚠️JAVAFX_INVERTED_AXIS_Y)
            objectMeshView.getTransforms().add(new Scale(scaleFactor, -scaleFactor, scaleFactor));
            return Collections.singletonList(objectMeshView);
        }
    }

    /**
     * Apply scale transformation to a mesh view
     */
    public void applyScale(MeshView meshView) {
        double scaleFactor = DomainProperties.getInstance().getScaleFactor();
        // ⚠️JAVAFX_INVERTED_AXIS_Y
        Scale scale = new Scale(scaleFactor, -scaleFactor, scaleFactor);
        meshView.getTransforms().add(scale);
    }
}

