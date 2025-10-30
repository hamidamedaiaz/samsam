package com.softpath.riverpath.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.softpath.riverpath.controller.BoundaryConditionController;
import com.softpath.riverpath.controller.BoundaryConditionGlobalController;
import com.softpath.riverpath.controller.BoundaryDefinitionController;
import com.softpath.riverpath.controller.HalfPlaneBoundaryController;
import com.softpath.riverpath.controller.LeftBottomPaneController;
import com.softpath.riverpath.controller.MainController;
import com.softpath.riverpath.controller.MeshingParametersController;
import com.softpath.riverpath.model.Boundary;
import com.softpath.riverpath.model.BoundaryCondition;
import com.softpath.riverpath.model.Coordinates;
import com.softpath.riverpath.model.CubeBoundary;
import com.softpath.riverpath.model.HalfPlaneBoundary;
import com.softpath.riverpath.model.ImmersedBoundary;
import com.softpath.riverpath.model.RadialBoundary;
import com.softpath.riverpath.model.ShapeType;
import com.softpath.riverpath.model.Simulation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.SPACE;

@Slf4j
@Getter
public class RunnerService {

    private final Map<String, String> blockMap = new HashMap<>();
    private final LeftBottomPaneController leftBottomPaneController;
    private final File workspaceDirectory;
    private final VelocityEngine velocityEngine;
    private final Map<String, Integer> indexByName = new HashMap<>();
    private final ObjectMapper mapper;

    public RunnerService(LeftBottomPaneController leftBottomPaneController, File workspaceDirectory) {
        mapper = new ObjectMapper();
        // Only these two configurations are necessary.
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // The rest of the constructor remains unchanged.
        parseBlocks("/filler_template/filler.txt");
        this.leftBottomPaneController = leftBottomPaneController;
        this.workspaceDirectory = workspaceDirectory;
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        velocityEngine.init();
    }

    /**
     * Initialize the boundary type (half plane, circle, cube, immersed) based on the type
     *
     * @param controller the boundary definition controller
     * @return the boundary object
     */
    private static Boundary initiateBoundaryType(BoundaryDefinitionController controller) {
        Boundary boundary;
        ShapeType type = controller.getComboBoxInitialValue();
        // initialize the boundary based on the type
        switch (type) {
            case Half_Plane:
                // Set normal vector
                Coordinates normal = new Coordinates();
                normal.setX(controller.getHalfPlaneBoundaryController().getNormalX().getText());
                normal.setY(controller.getHalfPlaneBoundaryController().getNormalY().getText());
                HalfPlaneBoundary halfPlaneBoundary = new HalfPlaneBoundary();
                halfPlaneBoundary.setNormal(normal);
                boundary = halfPlaneBoundary;
                break;
            case Circle:
            case Sphere:
                RadialBoundary radialBoundary = new RadialBoundary();
                radialBoundary.setRadius(controller.getCircleBoundaryController().getRadius().getText());
                boundary = radialBoundary;
                break;
            case Cube:
                CubeBoundary cubeBoundary = new CubeBoundary();
                cubeBoundary.setWidth(controller.getRectangleBoundaryController().getRectangleWidth().getText());
                cubeBoundary.setHeight(controller.getRectangleBoundaryController().getRectangleHeight().getText());
                cubeBoundary.setDepth("0"); // For a 2D cube
                boundary = cubeBoundary;
                break;
            case Immersed:
                ImmersedBoundary immersedBoundary = new ImmersedBoundary();
                immersedBoundary.setImmersedObjectFileName(controller.getImmersedBoundaryController().getMeshFileName());
                boundary = immersedBoundary;
                break;

            default:
                throw new IllegalStateException("Boundary type is not supported " + type);
        }
        return boundary;
    }

    public void generateAllMTCFiles(String domainMesh) {
        Simulation simulation = new Simulation();
        // set domain mesh file name
        simulation.setDomainMeshFile(domainMesh);
        // generate GeometresE.mtc
        mergeBoundaryDefTemplate(simulation);
        // generate CLMecanique.mtc
        mergeBoundaryConditionTemplate(simulation);
        // generate IHM.mtc
        mergeIHM(simulation);
        // generate DragLift.mtc & DeLaFonction.mtc
        mergeImmersedIndexTemplate(simulation);
        //generate output.mtc
        mergeOutputTemplate();

        try {
            mapper.writeValue(new File(workspaceDirectory, "simulation.json"), simulation);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeBoundaryDefTemplate(Simulation simulation) {
        int index = 1;
        StringBuilder allBoundaryDef = new StringBuilder();
        StringBuilder allBoundaryValues = new StringBuilder();
        for (BoundaryDefinitionController controller : leftBottomPaneController.getBoundaryDefinitionControllers()) {
            allBoundaryDef.append(addBlockUniqueParameter(index, "definition"));
            allBoundaryValues.append(generateDefinitionValue(index, controller));
            addBoundaryToSimulation(controller, simulation);
            indexByName.put(controller.getNameInitialValue(), index);
            index++;
        }
        // Create a VelocityContext and add the data
        VelocityContext context = new VelocityContext();
        context.put("definition", allBoundaryDef);
        context.put("values", allBoundaryValues);
        // Write merged output to the file
        mergeContextToTemplate("Geometrie", "GeometresE.mtc", "Geometrie.vm", context);
    }

    /**
     * Merge immersed index template
     *
     * @param simulation the simulation object
     */
    private void mergeImmersedIndexTemplate(Simulation simulation) {
        // TODO after KAN-57 : handle multiple immersed objects.
        //  Pick the first one but all the other must be managed later
        Boundary immersedObject = simulation.getBoundaries().stream()
                .filter(boundary -> boundary.getType() == ShapeType.Immersed)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No immersed object found"));
        // get the index of the immersed object
        Integer immersedId = indexByName.get(immersedObject.getName());
        if (immersedId != null) {
            VelocityContext context = new VelocityContext();
            context.put("immersedId", immersedId);
            // Merge the template with the context
            mergeContextToTemplate("DragLift", "DragLift.mtc", "DragLift.vm", context);
            mergeContextToTemplate("Maillage", "DeLaFonction.mtc", "DeLaFonction.vm", context);
        }
    }

    private void mergeOutputTemplate() {
        VelocityContext context = new VelocityContext();
        // Build the list of dynamic fields
        List<String> appartientList = new ArrayList<>();
        // Browse all boundaries in ID order
        leftBottomPaneController.getBoundaryDefinitionControllers().forEach(controller -> {
            String boundaryName = controller.getNameInitialValue();
            int id = indexByName.get(boundaryName);
            // TODO after KAN-57 : handle multiple immersed objects
            appartientList.add(addBlockUniqueParameter(id, "appartient_data"));
            // If it is an immersed object, also add LevelSetEntree
            if (controller.isImmersedObject()) {
                appartientList.add(addBlockUniqueParameter(id, "level_set_data"));
            }
        });
        context.put("dynamicFields", String.join("", appartientList));
        mergeContextToTemplate("IO", "output.mtc", "output.vm", context);
    }

    private void addBoundaryToSimulation(BoundaryDefinitionController controller, Simulation simulation) {
        // Check whether a boundary with this name already exists
        String boundaryName = controller.getNameInitialValue();

        // Delete any existing boundary with the same name to avoid data persistence
        simulation.getBoundaries().removeIf(boundary -> boundary.getName().equals(boundaryName));

        // Create a new boundary using the existing method
        Boundary boundary = initiateBoundaryType(controller);

        // Set common properties
        boundary.setName(boundaryName);

        // Set origin
        Coordinates origin = new Coordinates();
        origin.setX(controller.getOriginX().getText());
        origin.setY(controller.getOriginY().getText());
        origin.setZ(controller.getOriginZ().getText());
        boundary.setOrigin(origin);

        // Add to simulation
        simulation.getBoundaries().add(boundary);
    }

    private void mergeBoundaryConditionTemplate(Simulation simulation) {
        StringBuilder allBoundaryConditions = new StringBuilder();
        BoundaryConditionGlobalController globalController = leftBottomPaneController.getConditionGlobalController();
        List<String> appartientList = new ArrayList<>();
        for (BoundaryConditionController controller : globalController.getAllConditions()) {
            String boundaryDefId = controller.getLabel();
            int index = indexByName.get(boundaryDefId);
            // generate new condition block
            allBoundaryConditions.append(addNewCondition(controller, index));
            // add condition to appartientList list in CLMecanique.mtc
            appartientList.add(addBlockUniqueParameter(index, "appartient_data"));
            // add boundary condition to simulation json object
            addBoundaryCondition(controller, boundaryDefId, simulation);
        }
        // Create a VelocityContext and add the data
        VelocityContext context = new VelocityContext();
        context.put("conditions", allBoundaryConditions);
        context.put("appartient", String.join("", appartientList));
        // Write merged output to the file
        mergeContextToTemplate("Solveur", "CLMecanique.mtc", "CLMecanique.vm", context);
    }

    /**
     * Add new condition to CLMecanique.mtc
     * The conditionData list will be used to generate the condition block in the template
     *
     * @param controller the boundary condition controller
     * @param index      the index of the boundary
     * @return the condition block
     */
    private String addNewCondition(BoundaryConditionController controller, int index) {
        VelocityContext context = new VelocityContext();
        context.put("id", String.valueOf(index));
        context.put("degx", controller.getVxValue().getText() != null ? "Un" : "Zero");
        context.put("degy", controller.getVyValue().getText() != null ? "Un" : "Zero");
        context.put("pressureGiven", controller.getPressureValue().getText() != null ? "Un" : "Zero");
        context.put("vx", StringUtils.equals(controller.getVxValue().getText(), "1") ? "Un" : "Zero");
        context.put("vy", StringUtils.equals(controller.getVyValue().getText(), "1") ? "Un" : "Zero");
        context.put("pressureValue", StringUtils.equals(controller.getPressureValue().getText(), "1") ? "Un" : "Zero");
        context.put("priority", controller.getPriorityValue().getText());
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(context, writer, "Template name", blockMap.get("condition"));
        return writer.toString();
    }

    /**
     * Generate block with unique parameter (like ID or other)
     *
     * @param index     the index of the boundary
     * @param blockName the blockName name to generate
     * @return the new line of blockName
     */
    private String addBlockUniqueParameter(int index, String blockName) {
        VelocityContext conditionContext = new VelocityContext();
        conditionContext.put("id", String.valueOf(index));
        StringWriter conditionWriter = new StringWriter();
        velocityEngine.evaluate(conditionContext, conditionWriter, "Template name", blockMap.get(blockName));
        return conditionWriter.toString();
    }

    private void addBoundaryCondition(BoundaryConditionController controller, String boundaryDefId, Simulation simulation) {
        BoundaryCondition boundaryCondition = new BoundaryCondition();
        // Set name
        boundaryCondition.setName(controller.getConditionNameValue().getText());
        // Initialize velocity coordinates
        Coordinates velocity = new Coordinates();
        velocity.setX(controller.getVxValue().getText());
        velocity.setY(controller.getVyValue().getText());
        velocity.setZ(controller.getVzValue().getText());
        boundaryCondition.setVelocity(velocity);
        // Set other properties
        boundaryCondition.setPressure(controller.getPressureValue().getText());
        boundaryCondition.setPriority(controller.getPriorityValue().getText());
        // Add the condition to the boundary
        simulation.getBoundarybyName(boundaryDefId).setCondition(boundaryCondition);
    }

    private void mergeIHM(Simulation simulation) {
        VelocityContext context = new VelocityContext();
        // merge data from time discretization in cimlib template resources
        mergeTimeDiscretization(simulation, context);
        // merge data from data engineering in cimlib template resources
        mergeDataEngineering(simulation, context);
        // merge data from meshing parameter in cimlib template resources
        mergeMeshingParameter(simulation, context);
        // Write merged output to the file
        mergeContextToTemplate(null, "IHM.mtc", "IHM.vm", context);
    }

    /**
     * Merge meshing parameter to simulation object and UI resources
     *
     * @param simulation the simulation object
     * @param context    the velocity context
     */
    private void mergeMeshingParameter(Simulation simulation, VelocityContext context) {
        MeshingParametersController meshParams = leftBottomPaneController.getMeshingParametersController();
        String nbElements = meshParams.getNbElements().getText();
        String hMin = meshParams.getHMin().getText();
        String lMax = meshParams.getLMax().getText();
        String nScaling = meshParams.getNScaling().getText();
        String scaleNorme = meshParams.getScaleNorme().getText();
        String err1 = meshParams.getErr1().getText();
        String err2 = meshParams.getErr2().getText();
        String adaptateur = meshParams.getAdaptateur().getText();
        String lMin = meshParams.getLMin().getText();
        simulation.setNbElements(nbElements);
        simulation.setHMin(hMin);
        simulation.setLMax(lMax);
        simulation.setNScaling(nScaling);
        simulation.setScaleNorme(scaleNorme);
        simulation.setErr1(err1);
        simulation.setErr2(err2);
        simulation.setAdaptateur(adaptateur);
        simulation.setLMin(lMin);
        context.put("nbElements", nbElements);
        context.put("hMin", hMin);
        context.put("lMax", lMax);
        context.put("nScaling", nScaling);
        context.put("scaleNorme", scaleNorme);
        context.put("err1", err1);
        context.put("err2", err2);
        context.put("adaptateur", adaptateur);
        context.put("lMin", lMin);
    }

    /**
     * Merge data from data engineering pane to simulation object and UI resources
     *
     * @param simulation the simulation object
     * @param context    the velocity context
     */
    private void mergeDataEngineering(Simulation simulation, VelocityContext context) {
        String viscosity = leftBottomPaneController.getDataEngineeringController().getViscosity().getText();
        String density = leftBottomPaneController.getDataEngineeringController().getDensity().getText();
        simulation.setViscosity(viscosity);
        simulation.setDensity(density);
        context.put("viscosity", viscosity);
        context.put("density", density);
    }

    /**
     * Merge data from time discretization pane to simulation object and UI resources
     *
     * @param simulation the simulation object
     * @param context    the velocity context
     */
    private void mergeTimeDiscretization(Simulation simulation, VelocityContext context) {
        // TODO KAN-75 use simulation object instead ?
        // KAN-76 use text field since run button is available only if all title pane are validated
        String timeStep = leftBottomPaneController.getTimeDiscretizationController().getTimeStep().getText();
        String totalTime = leftBottomPaneController.getTimeDiscretizationController().getTotalTime().getText();
        String storageFrequency = leftBottomPaneController.getTimeDiscretizationController().getStorageFrequency().getText();
        simulation.setTimeStep(timeStep);
        simulation.setTotalTime(totalTime);
        simulation.setFrequency(storageFrequency);
        context.put("timeStep", timeStep);
        context.put("totalTime", totalTime);
        context.put("storageFrequency", storageFrequency);
    }

    private void mergeContextToTemplate(String parentDirectory, String fileName, String templateName, VelocityContext context) {
        File geoFile = new File(workspaceDirectory, parentDirectory == null ? fileName :
                parentDirectory + File.separator + fileName);
        try (Writer writer = new FileWriter(geoFile)) {
            Template template = velocityEngine.getTemplate(templateName);
            template.merge(context, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String generateDefinitionValue(int index, BoundaryDefinitionController controller) {
        VelocityContext context = new VelocityContext();
        context.put("id", String.valueOf(index));
        context.put("ox", controller.getOriginX().getText());
        context.put("oy", controller.getOriginY().getText());
        /* TODO KAN-38-FAC adapt after factorization
        if (controller.isNormal()) {
            context.put("AxeOrNormal", "Normale");
            context.put("coordinates", concateWithWhiteCharacter(controller.getNormalXInitialValue(),
                    controller.getNormalYInitialValue()));
        } else if (controller.isAxe()) {
            context.put("AxeOrNormal", "Axes");
            context.put("coordinates", concateWithWhiteCharacter(controller.getFirstAxe2DXInitialValue(),
                    controller.getFirstAxe2DYInitialValue(),
                    controller.getSecondAxe2DXInitialValue(),
                    controller.getSecondAxe2DYInitialValue()));
        }*/
        // handle axe or normal
        if (controller.getComboBoxInitialValue() == ShapeType.Half_Plane) {
            HalfPlaneBoundaryController halfPlaneController = controller.getHalfPlaneBoundaryController();
            context.put("AxeOrNormal", "Normale");
            context.put("coordinates", halfPlaneController.getNormalX().getText() + " "
                    + halfPlaneController.getNormalY().getText());
        }
        // set data form
        if (controller.isImmersedObject()) {
            context.put("AxeOrNormal", "Axes");
            // TODO after KAN-52 : handle axes coordinates
            context.put("coordinates", "1 0 0 1");
            context.put("geoDataValue", generateGeometreObjectBlock(index, controller));
        } else {
            context.put("geoDataValue", generateGeometreBoundaryBlock(index, controller));
        }
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(context, writer, "Template name", blockMap.get("value"));
        return writer.toString();
    }

    /**
     * Generate geometre object block
     *
     * @param index      the index of the boundary
     * @param controller the boundary definition controller
     * @return the geometre object block
     */
    private String generateGeometreObjectBlock(int index, BoundaryDefinitionController controller) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("id", String.valueOf(index));
        velocityContext.put("immersionFile", controller.getImmersedBoundaryController().getMeshFileName());
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(velocityContext, writer, "Template name", blockMap.get("geometre_object_data"));
        return writer.toString();
    }

    /**
     * Generate geometre boundary block
     *
     * @param index      the index of the boundary
     * @param controller the boundary definition controller
     * @return the geometre boundary block
     */
    private String generateGeometreBoundaryBlock(int index, BoundaryDefinitionController controller) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("murType", controller.getComboBoxInitialValue().getCimLibName());
        velocityContext.put("id", String.valueOf(index));
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(velocityContext, writer, "Template name", blockMap.get("geometre_boundary_data"));
        return writer.toString();
    }

    /**
     * Parse blocks from the template file and store them in the map
     *
     * @param filename the template file
     */
    private void parseBlocks(String filename) {
        try {
            // Reading the resource from the JAR
            try (InputStream is = RunnerService.class.getResourceAsStream(filename)) {
                if (is == null) {
                    throw new RuntimeException("Cannot find resource: " + filename);
                }

                // Use a BufferedReader to read line by line
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder blockContent = new StringBuilder();
                    String currentId = null;
                    boolean insideBlock = false;
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (StringUtils.startsWith(line, "start_block_")) {
                            // Extract ID from the start of the block
                            currentId = StringUtils.substring(line, "start_block_".length()).trim();
                            insideBlock = true;
                        } else if (StringUtils.startsWith(line, "end_block_")) {
                            // End of the block, store ID and content into the map
                            blockMap.put(currentId, blockContent.toString());
                            // Reset for the next block
                            blockContent.setLength(0);
                            insideBlock = false;
                        } else if (insideBlock) {
                            // Add line to the block content
                            blockContent.append(line).append("\n");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading template file", e);
        }
    }

    /**
     * Concatenate all values with white space
     *
     * @param values the values
     * @return the concatenated string
     */
    private String concateWithWhiteCharacter(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            builder.append(value).append(SPACE);
        }
        return builder.toString();
    }

    public Process startProcess(MainController mainController, File directory, List<String> command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (directory.exists() && directory.isDirectory()) {
                processBuilder.directory(directory);
            }
            return processBuilder.start();
        } catch (Exception e) {
            log.error("Error starting process:", e);
            mainController.displayMessageConsoleOutput(e.getMessage());
            return null;
        }
    }
}
