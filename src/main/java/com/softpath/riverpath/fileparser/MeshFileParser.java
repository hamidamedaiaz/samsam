package com.softpath.riverpath.fileparser;

import javafx.geometry.Point3D;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Class to parse .t and transform the content to MeshStructure object
 *
 * @author rhajou
 */
public class MeshFileParser {

    /**
     * Parse a .t file and return a MeshStructure object
     *
     * @param file the file to parse
     * @return a MeshStructure object
     */
    public static CFDTriangleMesh parseFile2TriangleMesh(File file) {
        CFDTriangleMesh triangleMesh = new CFDTriangleMesh();
        try (Scanner scanner = new Scanner(file)) {
            // Read metadata from the first line
            int numberOfPoints = scanner.nextInt();
            // unused metadata
            scanner.nextInt();
            // Skip first line
            scanner.nextLine();
            int currentLine = 0;
            // Read 3D coordinates from the remaining lines
            while (scanner.hasNextLine() && currentLine < numberOfPoints) {
                String line = scanner.nextLine();
                // Split the line into x and y coordinates
                String[] coordinates = line.split(StringUtils.SPACE);
                double zCoordinates;
                if (coordinates.length < 3) {
                    zCoordinates = 0;
                } else {
                    zCoordinates = Double.parseDouble(coordinates[2]);
                }
                Point3D point = new Point3D(Double.parseDouble(coordinates[0]),
                        Double.parseDouble(coordinates[1]),
                        zCoordinates);
                triangleMesh.addPoint(point, currentLine);
                currentLine++;
            }
            // handle faces of triangle mesh
            handleFaces(scanner, triangleMesh);
            triangleMesh.getTexCoords().addAll(1, 1);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return triangleMesh;
    }

    private static void handleFaces(Scanner scanner, CFDTriangleMesh triangleMesh) {
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            // Split the line into id1, id2 and id3
            String[] coordinates = line.split(StringUtils.SPACE);
            int vertex1 = Integer.parseInt(coordinates[0]) - 1;
            int vertex2 = Integer.parseInt(coordinates[1]) - 1;
            int vertex3 = Integer.parseInt(coordinates[2]) - 1;
            // Process the coordinates as needed
            if (vertex3 == -1) {
                // it's a border line and not a triangle => back to vertext1
                triangleMesh.addBorderLine(vertex1, vertex2);
            } else {
                // create a triangle face
                triangleMesh.addTriangleAndLines(vertex1, vertex2, vertex3);
            }
        }
    }
}
