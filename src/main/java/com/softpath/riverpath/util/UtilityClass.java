package com.softpath.riverpath.util;

import com.softpath.riverpath.custom.event.CustomEvent;
import com.softpath.riverpath.custom.event.EventManager;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.softpath.riverpath.custom.event.EventEnum.CONVERT_PYTHON_PROCESS_MESSAGE;

/**
 * Utility class - Optimized version for embedded Python
 *
 * @author rhajou
 */
public class UtilityClass {

    private static final String ZERO = "0";
    private static final String DOT = ".";
    private static final String EMPTY = "";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    public static File workspaceDirectory;
    // Cache for the embedded Python path
    private static String embeddedPythonPath = null;
    private static boolean pythonExtracted = false;

    /**
     * Create workspace project if not exist
     * The workspace project is created in the home directory
     * In workspace project create current simulation directory
     * workspace name = import + "_" + "yyyyMMddHHmmss"
     *
     * @param mshFile the msh file
     */
    public static void createWorkspace(File mshFile) {
        String workspaceName = FilenameUtils.getBaseName(mshFile.getName());
        File projectDirectory = new File(createOrGetHomeDirectory(), workspaceName);
        if (!projectDirectory.exists()) {
            projectDirectory.mkdir();
        }
        File workspaceDirectory = new File(projectDirectory, "import_" + LocalDateTime.now().format(FORMATTER));
        // create folder
        workspaceDirectory.mkdir();

        try {
            // Copy resources from JAR
            URL sourceUrl = UtilityClass.class.getClassLoader().getResource("workspace_template");
            if (sourceUrl == null) {
                throw new RuntimeException("workspace_template not found in resources");
            }

            if (sourceUrl.getProtocol().equals("jar")) {
                // If we are in a JAR
                copyResourcesFromJar(workspaceDirectory);
            } else {
                // If we are in development
                File sourceDirectory = new File(sourceUrl.getFile());
                FileUtils.copyDirectory(sourceDirectory, workspaceDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error copying workspace_template: " + e.getMessage());
        }
        UtilityClass.workspaceDirectory = workspaceDirectory;
    }

    private static void copyResourcesFromJar(File targetDir)  {
        // Reading JAR entries
        try (JarFile jarFile = new JarFile(new File(UtilityClass.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()))) {

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("workspace_template/") && !entry.isDirectory()) {
                    // Create parent folders if necessary
                    File targetFile = new File(targetDir, entry.getName().substring("workspace_template/".length()));
                    targetFile.getParentFile().mkdirs();

                    // Copy the file
                    try (InputStream jarIn = jarFile.getInputStream(entry);
                         FileOutputStream out = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = jarIn.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error accessing JAR", e);
        }
    }


    /**
     * Extracts and returns the path to the embedded Python executable.
     * Optimized version with selective extraction.
     *
     * @return The absolute path to the embedded Python executable.
     * @throws RuntimeException if embedded Python is not found or cannot be extracted.
     */
    private static String getEmbeddedPythonPath() {
        if (embeddedPythonPath != null && pythonExtracted) {
            return embeddedPythonPath;
        }

        try {
            URL pythonUrl = UtilityClass.class.getResource("/python/python.exe");

            if (pythonUrl != null) {
                return "C:\\Users\\user\\Desktop\\Jean_Sophtapth\\riverpath\\src\\main\\resources\\python\\python.exe";
            }

            // If we get here, embedded Python has not been found.
            throw new RuntimeException("Embedded Python not found in resources. " +
                    "Verify that the file /python/python.exe exists in src/main/resources/");

        } catch (Exception e) {
            throw new RuntimeException("Error accessing embedded Python: " + e.getMessage(), e);
        }
    }

    /**
     * Optimized extraction of Python - only essential files
     *
     * @return the path to the extracted Python executable
     * @throws IOException if extraction fails
     */
    private static String extractEmbeddedPythonOptimized() {
        if (embeddedPythonPath != null && pythonExtracted) {
            return embeddedPythonPath;
        }

        try {
            // Create a temporary folder for Python
            File tempPythonDir = new File(getHomeDirectory(), "python_embedded");
            if (!tempPythonDir.exists()) {
                tempPythonDir.mkdirs();
            }

            // Check if Python is already extracted and functional
            File existingPython = new File(tempPythonDir, "python.exe");
            if (existingPython.exists() && existingPython.canExecute()) {
                embeddedPythonPath = existingPython.getAbsolutePath();
                pythonExtracted = true;
                return embeddedPythonPath;
            }

            EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE,
                    "Embedded Python extraction..."));

            // Extract ONLY the essential files from the JAR
            try (JarFile jarFile = new JarFile(new File(UtilityClass.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()))) {

                Enumeration<JarEntry> entries = jarFile.entries();
                int extractedFiles = 0;

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();

                    // Filter ONLY essential files
                    if (entry.getName().startsWith("python/") && !entry.isDirectory() &&
                            isEssentialPythonFile(entry.getName())) {

                        // Create the destination file
                        String relativePath = entry.getName().substring("python/".length());
                        File targetFile = new File(tempPythonDir, relativePath);
                        targetFile.getParentFile().mkdirs();

                        // Extract the file
                        try (InputStream jarIn = jarFile.getInputStream(entry)) {
                            Files.copy(jarIn, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }

                        // Make executable if it is python.exe
                        if (targetFile.getName().equals("python.exe")) {
                            targetFile.setExecutable(true);
                            embeddedPythonPath = targetFile.getAbsolutePath();
                        }

                        extractedFiles++;
                    }
                }

                EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE,
                        "✅ " + extractedFiles + " Extracted Python files"));
            }

            if (embeddedPythonPath == null) {
                throw new RuntimeException("python.exe not found in python resources/");
            }

            pythonExtracted = true;
            return embeddedPythonPath;

        } catch (Exception e) {
            throw new RuntimeException("Error accessing JAR", e);
        }
    }

    /**
     * Determines whether a Python file is essential for execution.
     * Optimization: only extract what is necessary.
     */
    private static boolean isEssentialPythonFile(String fileName) {
        return fileName.matches("python/(python\\.exe|.*\\.dll|Lib/(site-packages/(numpy|gmsh)/.*|.*\\.py))");
    }

    public static String getResourcePath(String resource) {
        return Objects.requireNonNull(UtilityClass.class.getResource(resource)).getFile();
    }

    public static File exeTempFile() {
        try {
            URL exeuril = UtilityClass.class.getResource("/cimlib_runner/cimlib_CFD_driver.exe");
            assert exeuril != null;
            try (InputStream exef = exeuril.openStream()) {
                // Create a temporary file
                File tempFile = File.createTempFile("temp", ".exe");
                // Write the InputStream to the temporary file
                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = exef.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                return tempFile;
            }
        } catch (IOException e) {
            throw new RuntimeException("error running cimlib", e);
        }
    }

    // this method should be removed and the home directory should be created at installation time
    @Deprecated
    public static File createOrGetHomeDirectory() {
        File riverpathDirectory = getHomeDirectory();
        if (!riverpathDirectory.exists()) {
            // Attempt to create the directory
            riverpathDirectory.mkdir();
        }
        return riverpathDirectory;
    }

    public static File getHomeDirectory() {
        String homeDirectory = System.getProperty("user.home");
        return new File(homeDirectory, ".riverpath");
    }

    /**
     * Convert msh file to .t file using embedded python program gmsh4mtc.py
     *
     * @param selectedFile the selected msh file
     * @return the .t file
     * @throws RuntimeException if embedded Python is not available or if the conversion fails
     */
    public static String convertMshPython(File selectedFile) {
        // Get the path to embedded Python (raises an exception if not found)
        String pythonExecutable = getEmbeddedPythonPath();

        // Log for debugging
        EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE,
                "Conversion mesh: " + selectedFile.getName()));

        String fileExtentionT = buildTExtentionName(selectedFile);
        File pythonOutputFile = new File(workspaceDirectory, fileExtentionT);

        // Prepare the Python command
        List<String> command = Arrays.asList(
                pythonExecutable,
                "gmsh4mtc.py",
                selectedFile.getAbsolutePath(),
                pythonOutputFile.getAbsolutePath()
        );

        int exitCode = runCommand(workspaceDirectory, command, false);

        if (exitCode != 0) {
            throw new RuntimeException("Error converting mesh file to .t with embedded Python. " +
                    "Exit code: " + exitCode);
        }

        return fileExtentionT;
    }

    /**
     * Optimized version of runCommand with NumPy warning filtering
     */
    public static int runCommand(File directory, List<String> command) {
        return runCommand(directory, command, false);
    }

    /**
     * Executes a command with optimized message filtering.
     *
     * @param directory Working directory.
     * @param command   Command to execute.
     * @param silent    Silent mode (for warmup).
     * @return Exit code.
     */
    public static int runCommand(File directory, List<String> command, boolean silent) {
        int exitCode = -1;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (directory.exists() && directory.isDirectory()) {
                processBuilder.directory(directory);
            }

            // Environment optimizations
            processBuilder.environment().remove("PYTHONPATH");
            processBuilder.environment().remove("PYTHONHOME");
            // Remove NumPy warnings at source
            processBuilder.environment().put("PYTHONWARNINGS", "ignore::DeprecationWarning");
            // Optimizing Python performancew
            processBuilder.environment().put("PYTHONUNBUFFERED", "1");

            Process process = processBuilder.start();

            // Read stdout in an optimized way
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!silent) {
                        EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE, line));
                    }
                }
            }

            // Read stderr with smart filtering of warnings
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    if (!silent && shouldDisplayError(line)) {
                        EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE, "ERROR: " + line));
                    }
                }
            }

            exitCode = process.waitFor();
            process.destroy();

        } catch (Exception e) {
            if (!silent) {
                EventManager.fireCustomEvent(new CustomEvent(CONVERT_PYTHON_PROCESS_MESSAGE,
                        "Runtime error: " + e.getMessage()));
            }
            throw new RuntimeException(e);
        }
        return exitCode;
    }

    /**
     * Determines whether an error line should be displayed.
     * Filters non-critical NumPy warnings.
     */
    private static boolean shouldDisplayError(String line) {
        // Filter known NumPy warnings
        if (line.contains("DeprecationWarning") ||
                line.contains("Arrays of 2-dimensional vectors are deprecated") ||
                line.contains("in1d is deprecated") ||
                line.contains("Use arrays of 3-dimensional vectors instead") ||
                line.contains("Use `np.isin` instead")) {
            return false;
        }

        // Filter other non-critical warnings
        return !line.contains("FutureWarning") &&
                !line.contains("UserWarning") &&
                !line.contains("RuntimeWarning");// Show real errors
    }

    public static String buildTExtentionName(File selectedFile) {
        if (FilenameUtils.isExtension(selectedFile.getName(), "msh")) {
            return FilenameUtils.removeExtension(selectedFile.getName()) + ".t";
        } else {
            return selectedFile.getName();
        }
    }

    public static String checkInteger(String oldValue, String newValue) {
        oldValue = StringUtils.defaultIfBlank(oldValue.replaceAll("[^\\d]", EMPTY), ZERO);
        // Remove leading zeros
        newValue = StringUtils.defaultIfBlank(newValue.replaceAll("^0+", EMPTY), ZERO);
        if (!newValue.matches("\\d*")) {
            newValue = newValue.replaceAll("[^\\d]", EMPTY);
        }
        return StringUtils.defaultIfBlank(newValue, oldValue);
    }

    public static boolean checkNotBlank(TextField myTextField) {
        boolean isValid = false;
        String text = myTextField.getText();
        if (StringUtils.isBlank(text)) {
            flagTextFieldWarning(myTextField);
        } else {
            myTextField.setStyle("");
            isValid = true;
        }
        return isValid;
    }

    public static void flagTextFieldWarning(TextField myTextField) {
        myTextField.setStyle("-fx-border-color: red;");
    }

    public static void unflagTextFieldWarning(TextField myTextField) {
        myTextField.setStyle(null);
    }

    public static void handleTextWithDigitOnly(KeyEvent event) {
        if (!KeyCode.LEFT.equals(event.getCode()) &
                !KeyCode.RIGHT.equals(event.getCode()) &
                !KeyCode.UP.equals(event.getCode()) &
                !KeyCode.DOWN.equals(event.getCode())
        ) {
            TextField textField = (TextField) event.getSource();
            boolean startWithMinus = StringUtils.startsWith(textField.getText(), "-");
            textField.setText(StringUtils.removeStart(textField.getText(), "-"));

            // remove multiple dot
            String currentText = removeMultipleDot(textField);
            if (!startWithMinus || textField.getText().length() != 1) {
                if (!isValidDouble(currentText)) {
                    currentText = StringUtils.defaultIfBlank(currentText.replaceAll("[^\\d.]", EMPTY), ZERO);
                    // Handle case where text starts with dot
                    if (currentText.startsWith(".")) {
                        currentText = "0" + currentText;
                    }
                    textField.setText(currentText);
                    textField.positionCaret(textField.getText().length());
                } else if (StringUtils.startsWith(currentText, ZERO)
                        && !StringUtils.startsWith(currentText, "0.")
                        && !StringUtils.equals(currentText, ZERO)) {
                    textField.setText(StringUtils.removeStart(currentText, ZERO));
                    textField.positionCaret(textField.getText().length());
                }
            }
            if (startWithMinus) {
                textField.setText("-" + textField.getText());
            }
            if (textField.getText() != null) {
                textField.positionCaret(textField.getText().length());
            }
        }
    }

    public static boolean isValidDouble(String text) {
        try {
            if (StringUtils.isEmpty(text)) {
                return true; // Allow an empty field
            }
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Copy the content of a VBox to another VBox
     * The destination VBox is cleared before copying
     *
     * @param vboxOrigin      The source VBox
     * @param vboxDestination The destination VBox
     */
    public static void copyVbox(VBox vboxOrigin, VBox vboxDestination) {
        vboxDestination.getChildren().clear();
        for (Node node : vboxOrigin.getChildren()) {
            vboxDestination.getChildren().add(cloneNode(node));
        }
    }

    /**
     * Clone a node
     * Add other types as needed
     *
     * @param node The node to clone
     * @return The cloned node
     */
    public static Node cloneNode(Node node) {
        if (node instanceof Label original) {
            return new Label(original.getText());
        } else if (node instanceof Button original) {
            Button copy = new Button(original.getText());
            copy.setOnAction(original.getOnAction()); // Copy actions if needed
            return copy;
        } else if (node instanceof TextField original) {
            return new TextField(original.getText());
        } else if (node instanceof GridPane) {
            return cloneGridPane((GridPane) node);
        } else if (node instanceof VBox) {
            return cloneVBox((VBox) node);
        } else if (node instanceof HBox) {
            return cloneHBox((HBox) node);
        } else if (node instanceof Hyperlink) {
            return cloneHyperlink((Hyperlink) node); // Add handling for Hyperlink
        }
        throw new UnsupportedOperationException("Unsupported node type: " + node.getClass());
    }

    private static Hyperlink cloneHyperlink(Hyperlink original) {
        Hyperlink copy = new Hyperlink(original.getText());
        copy.setOnAction(original.getOnAction()); // Copy the action if it exists
        copy.setVisited(original.isVisited()); // Preserve visited state if necessary
        return copy;
    }

    private static GridPane cloneGridPane(GridPane original) {
        GridPane copy = new GridPane();

        // Recursively clone children and preserve layout constraints
        for (Node child : original.getChildren()) {
            Node clonedChild = cloneNode(child);
            if (clonedChild != null) {
                Integer row = GridPane.getRowIndex(child);
                Integer column = GridPane.getColumnIndex(child);
                Integer rowSpan = GridPane.getRowSpan(child);
                Integer columnSpan = GridPane.getColumnSpan(child);

                GridPane.setRowIndex(clonedChild, row);
                GridPane.setColumnIndex(clonedChild, column);
                GridPane.setRowSpan(clonedChild, rowSpan);
                GridPane.setColumnSpan(clonedChild, columnSpan);

                copy.getChildren().add(clonedChild);
            }
        }
        // Copy row and column constraints
        copy.getRowConstraints().addAll(original.getRowConstraints());
        copy.getColumnConstraints().addAll(original.getColumnConstraints());
        return copy;
    }

    private static VBox cloneVBox(VBox original) {
        VBox copy = new VBox();
        // Copy properties
        copy.setSpacing(original.getSpacing());
        copy.setAlignment(original.getAlignment());

        // Recursively clone children
        for (Node child : original.getChildren()) {
            Node clonedChild = cloneNode(child);
            if (clonedChild != null) {
                copy.getChildren().add(clonedChild);
            }
        }

        return copy;
    }

    private static HBox cloneHBox(HBox original) {
        HBox copy = new HBox();
        // Copy properties
        copy.setSpacing(original.getSpacing());
        copy.setAlignment(original.getAlignment());

        // Recursively clone children
        for (Node child : original.getChildren()) {
            Node clonedChild = cloneNode(child);
            if (clonedChild != null) {
                copy.getChildren().add(clonedChild);
            }
        }

        return copy;
    }

    private static String removeMultipleDot(TextField textField) {
        String currentText = textField.getText();
        int numberOf = StringUtils.countMatches(currentText, DOT);
        if (numberOf > 1) {
            int firstIndex = StringUtils.indexOf(currentText, DOT);
            currentText = StringUtils.remove(currentText, DOT);
            currentText = StringUtils.overlay(currentText, DOT, firstIndex, firstIndex);
            currentText = StringUtils.defaultIfBlank(currentText.replaceAll("[^\\d.]", EMPTY), ZERO);
            textField.setText(currentText);
            textField.positionCaret(textField.getText().length());
        }
        return currentText;
    }
}