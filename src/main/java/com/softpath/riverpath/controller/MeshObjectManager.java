package com.softpath.riverpath.controller;

import com.softpath.riverpath.fileparser.CFDTriangleMesh;
import com.softpath.riverpath.model.Coordinates;
import com.softpath.riverpath.util.ColorObjectHandler;
import com.softpath.riverpath.util.DisplayMode;
import com.softpath.riverpath.util.DomainProperties;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all immersed objects  with their properties
    meshes
  display names
  display modes
   colors
 */
public class MeshObjectManager {

    private final ColorObjectHandler colorObjectHandler = new ColorObjectHandler();

    @Getter
    private final Map<String, CFDTriangleMesh> allMeshes = new HashMap<>();
    @Getter
    private final Map<String, String> objectDisplayNames = new HashMap<>();
    @Getter
    private final Map<String, DisplayMode> objectDisplayModes = new HashMap<>();
    @Getter
    private final Map<String, Shape> shapes = new HashMap<>();
    @Getter
    private final Map<String, Group> normalArrows = new HashMap<>();

    /**
     * Add an immersed object to the manager
     */
    public void addObject(String controllerID, CFDTriangleMesh objectMesh, Coordinates origin, Color existingColor) {
        objectMesh.setScale(DomainProperties.getInstance().getScaleFactor());
        objectMesh.setPosition(new Point3D(parseDouble(origin.getX()), parseDouble(origin.getY()), parseDouble(origin.getZ())));

        if (existingColor != null) {
            objectMesh.setColor(existingColor);
        } else {
            objectMesh.setColor(colorObjectHandler.getNextColor());
        }

        allMeshes.put(controllerID, objectMesh);
    }

    /**
     * Store the display name for an object
     */
    public void setDisplayName(String controllerId, String displayName) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            objectDisplayNames.put(controllerId, displayName);
        }
    }

    /**
     * Set the display mode for an object
     */
    public void setDisplayMode(String objectId, DisplayMode mode) {
        objectDisplayModes.put(objectId, mode);
    }

    /**
     * Get the display mode for an object (default: MESH)
     */
    public DisplayMode getDisplayMode(String objectId) {
        return objectDisplayModes.getOrDefault(objectId, DisplayMode.MESH);
    }

    /**
     * Get the display name for an object
     */
    public String getDisplayName(String objectId) {
        return objectDisplayNames.getOrDefault(objectId, objectId);
    }

    /**
     * Remove an object from the manager
     */
    public void removeObject(String objectId) {
        allMeshes.remove(objectId);
        objectDisplayNames.remove(objectId);
        objectDisplayModes.remove(objectId);
    }

    /**
     * Add a shape to the manager
     */
    public void addShape(String id, Shape shape) {
        shapes.put(id, shape);
    }

    /**
     * Remove a shape from the manager
     */
    public void removeShape(String id) {
        shapes.remove(id);
    }

    /**
     * Add a normal arrow to the manager
     */
    public void addNormalArrow(String id, Group normalArrow) {
        normalArrows.put(id, normalArrow);
    }

    /**
     * Remove a normal arrow from the manager
     */
    public void removeNormalArrow(String id) {
        normalArrows.remove(id);
    }

    /**
     * Clear all objects
     */
    public void clear() {
        allMeshes.clear();
        objectDisplayNames.clear();
        objectDisplayModes.clear();
    }

    /**
     * Check if there are any objects
     */
    public boolean hasObjects() {
        return !allMeshes.isEmpty();
    }

    /**
     * Get the existing color of an object
     */
    public Color getExistingColor(String objectId) {
        CFDTriangleMesh mesh = allMeshes.get(objectId);
        return mesh != null ? mesh.getColor() : null;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

