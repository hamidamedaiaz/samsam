package com.softpath.riverpath.controller;

import com.softpath.riverpath.custom.pane.ZoomableScrollPane;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Controller for the mesh display
 *
 * @author rhajou
 */
@NoArgsConstructor
public class MeshDisplayController {

    private static final double CAMERA_NEAR_CLIP = 0.1;
    private static final double CAMERA_FAR_CLIP = 10000.0;

    private static final double MOUSE_SPEED = 0.1;
    private static final double ROTATION_SPEED = 2.0;
    private static final double ZOOM_SPEED = 0.1;

    @Getter
    private Node target;
    private Group sceneRoot;
    private PerspectiveCamera camera;
    private SubScene subScene;

    private double mouseOldX, mouseOldY;
    private double initialCameraDistance;
    private final double minZoom = 0.1;
    private final double maxZoom = 10.0;
    private double currentZoom = 1.0;

    private final Scale sceneScale = new Scale(1, 1, 1);
    private double translateX = 0;
    private double translateY = 0;

    @FXML
    private ZoomableScrollPane scrollablePane;

    /**
     * Apply the zoomable pane to the target node
     *
     * @param target the target node
     */
    public void applyPaneView(Node target) {
        this.target = target;

        Group targetGroup = new Group(target);
        sceneRoot = new Group(new Group(targetGroup));

        sceneRoot.getTransforms().add(sceneScale);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(CAMERA_NEAR_CLIP);
        camera.setFarClip(CAMERA_FAR_CLIP);
        camera.setFieldOfView(30);

        subScene = new SubScene(sceneRoot, 0, 0, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.WHITE);
        subScene.setCamera(camera);

        subScene.widthProperty().bind(scrollablePane.widthProperty());
        subScene.heightProperty().bind(scrollablePane.heightProperty());
        handleMouse();

        scrollablePane.setContent(subScene);

        Platform.runLater(() -> {
            centerTarget(targetGroup);
            adjustCameraDistance();
            initialCameraDistance = -camera.getTranslateZ();
            zoom(1.001);
        });
    }

    private void centerTarget(Group targetGroup) {
        Bounds bounds = target.getBoundsInParent();
        double centerX = (bounds.getMinX() + bounds.getMaxX()) / 2;
        double centerY = (bounds.getMinY() + bounds.getMaxY()) / 2;
        double centerZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;

        Translate translate = new Translate(-centerX, -centerY, -centerZ);
        targetGroup.getTransforms().add(translate);
    }

    private void adjustCameraDistance() {
        Bounds bounds = target.getBoundsInParent();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        double fov = camera.getFieldOfView();
        double aspectRatio = subScene.getWidth() / subScene.getHeight();
        double vFovRadians = Math.toRadians(fov);
        double hFovRadians = 2 * Math.atan(Math.tan(vFovRadians / 2) * aspectRatio);
        double distanceV = (height / 2) / Math.tan(vFovRadians / 2);
        double distanceH = (width / 2) / Math.tan(hFovRadians / 2);
        double distance = Math.max(distanceV, distanceH) * 1.05;
        camera.setTranslateZ(-distance);
    }

    private void handleMouse() {
        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        Translate translate = new Translate(0, 0);
        sceneRoot.getTransforms().addAll(rotateX, rotateY, translate);

        subScene.setOnMousePressed(me -> {
            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        subScene.setOnMouseDragged(me -> {
            double mouseDeltaX = (me.getSceneX() - mouseOldX);
            double mouseDeltaY = (me.getSceneY() - mouseOldY);

            if (me.isPrimaryButtonDown()) {
                rotateY.setAngle(rotateY.getAngle() - mouseDeltaX * MOUSE_SPEED * ROTATION_SPEED);
                rotateX.setAngle(rotateX.getAngle() + mouseDeltaY * MOUSE_SPEED * ROTATION_SPEED);
            } else if (me.isSecondaryButtonDown()) {
                translateX += mouseDeltaX * MOUSE_SPEED;
                translateY += mouseDeltaY * MOUSE_SPEED;
                translate.setX(translateX);
                translate.setY(translateY);
            }

            mouseOldX = me.getSceneX();
            mouseOldY = me.getSceneY();
        });

        subScene.setOnScroll((ScrollEvent event) -> {
            event.consume();
            double zoomFactor = Math.exp(event.getDeltaY() * ZOOM_SPEED / 30);
            zoom(zoomFactor);
        });

        subScene.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2) {
                resetView();
            }
        });
    }

    private void zoom(double factor) {
        currentZoom *= factor;
        currentZoom = Math.max(minZoom, Math.min(maxZoom, currentZoom));
        sceneScale.setX(currentZoom);
        sceneScale.setY(currentZoom);
        sceneScale.setZ(currentZoom);
    }

    private void resetView() {
        currentZoom = 1.1;
        sceneScale.setX(1);
        sceneScale.setY(1);
        //sceneScale.setZ(1);
        sceneRoot.getTransforms().stream()
                .filter(t -> t instanceof Rotate)
                .forEach(t -> ((Rotate) t).setAngle(0));
        translateX = 0;
        translateY = 0;
        sceneRoot.getTransforms().stream()
                .filter(t -> t instanceof Translate)
                .forEach(t -> {
                    ((Translate) t).setX(0);
                    ((Translate) t).setY(0);
                });
        scrollablePane.setHvalue(0.5);
        scrollablePane.setVvalue(0.5);
    }
}