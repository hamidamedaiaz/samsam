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
            if (triangleMesh.is3D()) {
                parseFaces3D(coordinates, triangleMesh);
            } else {
                parseFaces2D(coordinates, triangleMesh);
            }
        }
    }

    private static void parseFaces2D(String[] coordinates, CFDTriangleMesh triangleMesh) {
        {
            int v1 = Integer.parseInt(coordinates[0]) - 1;
            int v2 = Integer.parseInt(coordinates[1]) - 1;
            int v3 = Integer.parseInt(coordinates[2]) - 1;
            if (v3 == -1) {
                triangleMesh.addBorderLine(v1, v2);
            } else {
                triangleMesh.addTriangleAndLines(v1, v2, v3);
            }
        }
    }

    private static void parseFaces3D(String[] coordinates, CFDTriangleMesh triangleMesh) {
        {
            int v1 = Integer.parseInt(coordinates[0]) - 1;
            int v2 = Integer.parseInt(coordinates[1]) - 1;
            int v3 = Integer.parseInt(coordinates[2]) - 1;
            int last = Integer.parseInt(coordinates[3]) - 1;
            if (last == -1) {
                triangleMesh.addTriangleAndLines3D(v1, v2, v3);
            } else {

                triangleMesh.addTriangleAndLines(v1, v2, v3);
                triangleMesh.addTriangleAndLines(v1, v2, last);
                triangleMesh.addTriangleAndLines(v2, v3, last);
                triangleMesh.addTriangleAndLines(v3, v1, last);
            }
        }
    }
}
