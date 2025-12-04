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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.softpath.riverpath.custom.event.EventEnum.CONVERT_PYTHON_PROCESS_MESSAGE;
import static org.apache.commons.lang3.Strings.CS;

/**
 * Utility class - Optimized version for embedded Python
 *
 * @author rhajou
 */
public class UtilityClass {

    public static File workspaceDirectory;
    private static final String ZERO = "0";
    private static final String DOT = ".";
    private static final String EMPTY = "";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
            // Template is located next to the app executable (installed by jpackage)
            // In development: Use resources
            // In production: Use installed directory
            File sourceDirectory = getWorkspaceTemplateDirectory();
            if (!sourceDirectory.exists()) {
                throw new RuntimeException("workspace_template not found at: " + sourceDirectory.getAbsolutePath());
            }
            FileUtils.copyDirectory(sourceDirectory, workspaceDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Error copying workspace_template: " + e.getMessage());
        }
        UtilityClass.workspaceDirectory = workspaceDirectory;
    }

    /**
     * Get the application directory where resources are located.
     * In development mode: returns current directory
     * In production mode (jpackage): returns the app directory set by jpackage
     *
     * @return File pointing to the app directory
     */
    private static File getAppDirectory() {
        // Production: jpackage sets this property via -Dapp.dir=$APPDIR
        String appDir = System.getProperty("app.dir");
        if (appDir != null) {
            return new File(appDir);
        }

        // Development: use current directory
        return new File(System.getProperty("user.dir"));
    }

    /**
     * Get the workspace template directory.
     * In development mode: reads from resources
     * In production mode: reads from installed app directory
     *
     * @return File pointing to workspace_template directory
     */
    private static File getWorkspaceTemplateDirectory() {
        // Try production path first (in app directory)
        File productionPath = new File(getAppDirectory(), "workspace_template");
        if (productionPath.exists()) {
            return productionPath;
        }

        // Fall back to development path (resources)
        URL resourceUrl = UtilityClass.class.getClassLoader().getResource("workspace_template");
        if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
            return new File(resourceUrl.getFile());
        }

        // Return production path even if it doesn't exist (will trigger error message)
        return productionPath;
    }

    /**
     * Extracts and returns the path to the embedded Python executable.
     * Optimized version with selective extraction.
     *
     * @return The absolute path to the embedded Python executable.
     * @throws RuntimeException if embedded Python is not found or cannot be extracted.
     */
    private static String getEmbeddedPythonPath() {
        return Paths.get(getAppDirectory().getAbsolutePath(), "gmsh4mtc", "gmsh4mtc.exe")
                .toAbsolutePath()
                .toString();
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
     * Convert msh file to .t file using embedded python program gmsh4mtc.exe
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
            boolean startWithMinus = CS.startsWith(textField.getText(), "-");
            textField.setText(CS.removeStart(textField.getText(), "-"));

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
                } else if (CS.startsWith(currentText, ZERO)
                        && !CS.startsWith(currentText, "0.")
                        && !CS.equals(currentText, ZERO)) {
                    textField.setText(CS.removeStart(currentText, ZERO));
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

    /**
     * @param textField a field element that is being checked by
     *                  {@link #handleTextWithDigitOnly(KeyEvent)}.
     *                  We reformat the text to a standard Java double print
     *                  for all cases, when focus is lost.
     */
    public static void prettyPrintDouble(TextField textField) {
        try {
            double prettyVal = Double.parseDouble(textField.getText());
            textField.setText(String.valueOf(prettyVal));
        } catch (Exception ignored) {
        }
    }
}