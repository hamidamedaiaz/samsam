package com.softpath.riverpath.fileparser;

import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Line;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class to store metadata of a mesh
 *
 * @author rhajou
 */
@EqualsAndHashCode(callSuper = true)
public class CFDTriangleMesh extends TriangleMesh {
    // map vertex id to 3D point
    @Getter
    private final Map<Integer, Point3D> vertexMap = new HashMap<>();
    // list of triangles / faces in the mesh
    @Getter
    private final List<MyTriangle> triangles = new ArrayList<>();
    @Getter
    private final List<MyTriangle> borderLines = new ArrayList<>();
    @Getter
    @Setter
    private double scale;
    private boolean is3D = false;

    /**
     * Add a point to the mesh
     * Add the point to the vertex map
     *
     * @param point    the point coordinates to add
     * @param vertexId vertex ID
     */
    public void addPoint(Point3D point, int vertexId) {
        getPoints().addAll((float) point.getX(), (float) point.getY(), (float) point.getZ());
        vertexMap.put(vertexId, new Point3D(point.getX(), point.getY(), point.getZ()));
        if (point.getZ() != 0) {
            is3D = true;
        }
    }

    /**
     * Add a borderline. This will help us later to identify easily the border of the mesh
     *
     * @param vertex1 first vertex of the line
     * @param vertex2 second vertex of the line
     */
    public void addBorderLine(int vertex1, int vertex2) {
        // it's a border line and not a triangle => back to vertext1
        borderLines.add(new MyTriangle(vertex1, vertex2, vertex1));
    }

    /**
     * Add a triangle / face to the mesh based on 3 vertices
     *
     * @param vertex1 first vertex of the triangle
     * @param vertex2 second vertex of the triangle
     * @param vertex3 third vertex of the triangle
     */
    public void addTriangleAndLines(int vertex1, int vertex2, int vertex3) {
        getFaces().addAll(vertex1, 0, vertex2, 0, vertex3, 0);
        triangles.add(new MyTriangle(vertex1, vertex2, vertex3));
    }

    /**
     * Add a new object to the current mesh
     *
     * @param objectMesh the object to add
     * @return the new mesh with the new object
     */
    public CFDTriangleMesh merge(CFDTriangleMesh objectMesh) {
        // clone this object
        CFDTriangleMesh combinedMesh = new CFDTriangleMesh();
        // add all points of the current object
        combinedMesh.getPoints().addAll(this.getPoints());
        // add all vertex map of the current object
        combinedMesh.getVertexMap().putAll(this.getVertexMap());
        // add all triangles / faces of the current object
        combinedMesh.getFaces().addAll(this.getFaces());
        // add texCoords of the current object
        combinedMesh.getTexCoords().addAll(this.getTexCoords());

        int nbVertex = vertexMap.size();
        objectMesh.getVertexMap().forEach((key, value) ->
                combinedMesh.getVertexMap().put(key + nbVertex, value));
        combinedMesh.getTexCoords().addAll(objectMesh.getTexCoords());

        objectMesh.getTriangles().forEach(triangle -> {
            combinedMesh.addTriangleAndLines(
                    triangle.getVertex1() + nbVertex,
                    triangle.getVertex2() + nbVertex,
                    triangle.getVertex3() + nbVertex
            );
        });
        return combinedMesh;
    }

    /**
     * Parse borderlines represented as triangles and create colored lines
     *
     * @return the lines
     */
    private Set<EdgeKey> detectBorderEdges() {
        // Finding the limits of volume
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Point3D point : vertexMap.values()) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }

        Set<EdgeKey> borderEdges = new HashSet<>();
        double tolerance = 0.001; // Tolérance pour la comparaison des flottants

        // For each triangle
        for (MyTriangle triangle : triangles) {
            checkAndAddBorderEdge(triangle.vertex1, triangle.vertex2, minX, maxX, minY, maxY, minZ, maxZ, tolerance, borderEdges);
            checkAndAddBorderEdge(triangle.vertex2, triangle.vertex3, minX, maxX, minY, maxY, minZ, maxZ, tolerance, borderEdges);
            checkAndAddBorderEdge(triangle.vertex3, triangle.vertex1, minX, maxX, minY, maxY, minZ, maxZ, tolerance, borderEdges);
        }

        return borderEdges;
    }

    private void checkAndAddBorderEdge(int v1, int v2, double minX, double maxX, double minY, double maxY,
                                       double minZ, double maxZ, double tolerance, Set<EdgeKey> borderEdges) {
        Point3D p1 = vertexMap.get(v1);
        Point3D p2 = vertexMap.get(v2);

        // An edge is on the contour if its two points are on the same outer face.
        boolean isOnXFace = (Math.abs(p1.getX() - minX) < tolerance && Math.abs(p2.getX() - minX) < tolerance) ||
                (Math.abs(p1.getX() - maxX) < tolerance && Math.abs(p2.getX() - maxX) < tolerance);

        boolean isOnYFace = (Math.abs(p1.getY() - minY) < tolerance && Math.abs(p2.getY() - minY) < tolerance) ||
                (Math.abs(p1.getY() - maxY) < tolerance && Math.abs(p2.getY() - maxY) < tolerance);

        boolean isOnZFace = (Math.abs(p1.getZ() - minZ) < tolerance && Math.abs(p2.getZ() - minZ) < tolerance) ||
                (Math.abs(p1.getZ() - maxZ) < tolerance && Math.abs(p2.getZ() - maxZ) < tolerance);

        if (isOnXFace || isOnYFace || isOnZFace) {
            borderEdges.add(new EdgeKey(v1, v2));
        }
    }

    /**
     * Parse all triangles and create lines
     *
     * @param scale the scale of the line
     * @param color the line color
     * @return the lines
     */
    public List<Node> createColoredLines(double scale, Color color) {
        List<Node> lines = new ArrayList<>();
        triangles.forEach(triangle -> {
            lines.add(createLine(triangle.getVertex1(), triangle.getVertex2(), scale, color));
            lines.add(createLine(triangle.getVertex2(), triangle.getVertex3(), scale, color));
            lines.add(createLine(triangle.getVertex3(), triangle.getVertex1(), scale, color));
        });
        return lines;
    }

    public List<Node> createColoredBorderLines(double scale, Color color) {
        List<Node> lines = new ArrayList<>();
        if (is3D) {
            Set<EdgeKey> borderEdges = detectBorderEdges();
            for (EdgeKey edge : borderEdges) {
                lines.add(createLine3D(edge.v1, edge.v2, scale, color));
            }
        } else {
            borderLines.forEach(
                    triangle -> lines.add(createLine(triangle.getVertex1(), triangle.getVertex2(), scale, color))
            );
        }
        return lines;
    }

    private Node createLine3D(int vertex1, int vertex2, double scale, Color color) {
        Point3D point1 = getVertexCoordinates(vertex1);
        Point3D point2 = getVertexCoordinates(vertex2);

        // Create a 3D line using a thin cylinder
        Cylinder line3D = new Cylinder(0.1, 1); // Rayon très fin
        line3D.setMaterial(new PhongMaterial(color));

        // Calculate the center point and length
        Point3D midpoint = point1.midpoint(point2);
        Point3D direction = point2.subtract(point1);
        double length = direction.magnitude();

        // Calculate rotation
        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D rotationAxis = yAxis.crossProduct(direction);
        double angle = Math.acos(yAxis.dotProduct(direction.normalize()));

        // Apply transformations
        line3D.getTransforms().addAll(
                new Translate(midpoint.getX() * scale, -midpoint.getY() * scale, midpoint.getZ() * scale),
                new Rotate(-Math.toDegrees(angle), rotationAxis),
                new Scale(1, length * scale, 1)
        );

        return line3D;
    }

    private Line createLine(int vertex1, int vertex2, double scale, Color color) {
        Point3D point1 = getVertexCoordinates(vertex1);
        Point3D point2 = getVertexCoordinates(vertex2);

        Line line = new Line(
                point1.getX() * scale, -point1.getY() * scale,
                point2.getX() * scale, -point2.getY() * scale
        );
        line.setStroke(color);

        double distance = Math.sqrt(
                Math.pow((point1.getX() - point2.getX()) * scale, 2) +
                        Math.pow((point1.getY() - point2.getY()) * scale, 2) +
                        Math.pow((point1.getZ() - point2.getZ()) * scale, 2)
        );

        double baseStrokeWidth = 0.1;
        line.setStrokeWidth(baseStrokeWidth * distance);
        return line;
    }

    /**
     * Get the 3D point of a vertex
     *
     * @param vertex the vertex
     * @return the 3D point
     */
    private Point3D getVertexCoordinates(int vertex) {
        return vertexMap.get(vertex);
    }

    public boolean is3D() {
        return is3D;
    }

    /**
     * Define a triangle object with 3 vertices
     *
     * @author rhajou
     */
    @Getter
    @AllArgsConstructor
    private static class MyTriangle {
        private final int vertex1;
        private final int vertex2;
        private final int vertex3;
    }

    @Getter
    private static class EdgeKey {
        private final int v1, v2;

        public EdgeKey(int v1, int v2) {
            // Toujours stocker dans l'ordre pour assurer l'unicité
            if (v1 < v2) {
                this.v1 = v1;
                this.v2 = v2;
            } else {
                this.v1 = v2;
                this.v2 = v1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return v1 == edgeKey.v1 && v2 == edgeKey.v2;
        }

        @Override
        public int hashCode() {
            return Objects.hash(v1, v2);
        }
    }
}