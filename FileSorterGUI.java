import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

// GUI
public class FileSorterGUI extends JFrame {

    private boolean separateByExtension = false;
    private Map<String, java.util.List<String>> customCategories = new java.util.HashMap<>();

    private JTextField downloadsField;
    private JTextField targetField;
    private JTextField intervalField;

    private ScheduledExecutorService scheduler;

    public FileSorterGUI() {
        setTitle("Smart File Sorter (NIO + Popups)");
        setMinimumSize(new Dimension(700, 300));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));
        initDefaultCategories();

        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));

        // Downloads/source folder
        JPanel downloadsPanel = new JPanel(new BorderLayout(5, 5));
        downloadsPanel.add(new JLabel("Source (Downloads) Folder: "), BorderLayout.WEST);
        downloadsField = new JTextField(System.getProperty("user.home") + "/Downloads");
        JButton browseDownloads = new JButton("Browse");
        downloadsPanel.add(downloadsField, BorderLayout.CENTER);
        downloadsPanel.add(browseDownloads, BorderLayout.EAST);

        // Target/sorted folder
        JPanel targetPanel = new JPanel(new BorderLayout(5, 5));
        targetPanel.add(new JLabel("Target (Sorted) Folder: "), BorderLayout.WEST);
        targetField = new JTextField(System.getProperty("user.home") + "/SortedFiles");
        JButton browseTarget = new JButton("Browse");
        targetPanel.add(targetField, BorderLayout.CENTER);
        targetPanel.add(browseTarget, BorderLayout.EAST);

        topPanel.add(downloadsPanel);
        topPanel.add(targetPanel);

        add(topPanel, BorderLayout.NORTH);

        // Bottom panel: controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton sortOnceButton = new JButton("Sort Now");
        JButton startAutoButton = new JButton("Start Auto");
        JButton stopAutoButton = new JButton("Stop Auto");

        controlPanel.add(sortOnceButton);
        controlPanel.add(startAutoButton);
        controlPanel.add(stopAutoButton);

        controlPanel.add(new JLabel("Every (min):"));
        intervalField = new JTextField("5", 4);
        controlPanel.add(intervalField);

        JButton customizeButton = new JButton("Customize Categories");
        JCheckBox separateCheck = new JCheckBox("Separate by extension");

        controlPanel.add(customizeButton);
        controlPanel.add(separateCheck);

        add(controlPanel, BorderLayout.SOUTH);

        browseDownloads.addActionListener(e -> chooseFolder(downloadsField));
        browseTarget.addActionListener(e -> chooseFolder(targetField));

        customizeButton.addActionListener(e -> openCategoryDialog());
        separateCheck.addActionListener(e -> separateByExtension = separateCheck.isSelected());

        sortOnceButton.addActionListener(this::handleSortOnce);
        startAutoButton.addActionListener(this::handleStartAuto);
        stopAutoButton.addActionListener(e -> stopAutoSort());

        pack();
        setLocationRelativeTo(null);
    }

    private void initDefaultCategories() {
        if (!customCategories.isEmpty()) return; // do not override user changes

        customCategories.put("Documents", new java.util.ArrayList<>(java.util.Arrays.asList(
                "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "odt", "rtf"
        )));
        customCategories.put("Images", new java.util.ArrayList<>(java.util.Arrays.asList(
                "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico", "tiff", "raw"
        )));
        customCategories.put("Videos", new java.util.ArrayList<>(java.util.Arrays.asList(
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg"
        )));
        customCategories.put("Audio", new java.util.ArrayList<>(java.util.Arrays.asList(
                "mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "opus"
        )));
        customCategories.put("Archives", new java.util.ArrayList<>(java.util.Arrays.asList(
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso"
        )));
        customCategories.put("Programs", new java.util.ArrayList<>(java.util.Arrays.asList(
                "exe", "msi", "apk", "deb", "rpm", "dmg", "pkg", "jar"
        )));
        customCategories.put("Others", new java.util.ArrayList<>());
    }

    private void openCategoryDialog() {
        JDialog dialog = new JDialog(this, "Customize Folders & Extensions", true);
        dialog.setSize(650, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        DefaultListModel<String> folderModel = new DefaultListModel<>();
        for (String cat : customCategories.keySet()) {
            folderModel.addElement(cat);
        }
        JList<String> folderList = new JList<>(folderModel);
        folderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel folderPanel = new JPanel(new BorderLayout(5, 5));
        folderPanel.add(new JLabel("Folders (Categories):"), BorderLayout.NORTH);
        folderPanel.add(new JScrollPane(folderList), BorderLayout.CENTER);

        JPanel folderBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton addFolderBtn = new JButton("Add Folder");
        JButton removeFolderBtn = new JButton("Del Folder");
        JButton renameFolderBtn = new JButton("Rename");

        folderBtnPanel.add(addFolderBtn);
        folderBtnPanel.add(removeFolderBtn);
        folderBtnPanel.add(renameFolderBtn);

        folderPanel.add(folderBtnPanel, BorderLayout.SOUTH);

        dialog.add(folderPanel, BorderLayout.WEST);

        DefaultListModel<String> extModel = new DefaultListModel<>();
        JList<String> extList = new JList<>(extModel);
        extList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel extPanel = new JPanel(new BorderLayout(5, 5));
        extPanel.add(new JLabel("Extensions for selected folder:"), BorderLayout.NORTH);
        extPanel.add(new JScrollPane(extList), BorderLayout.CENTER);

        JPanel extBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JTextField extField = new JTextField(10);
        JButton addExtBtn = new JButton("Add Ext");
        JButton removeExtBtn = new JButton("Del Ext");
        JButton sortNowBtn = new JButton("Sort Now (using this config)");

        extBtnPanel.add(new JLabel("Ext:"));
        extBtnPanel.add(extField);
        extBtnPanel.add(addExtBtn);
        extBtnPanel.add(removeExtBtn);
        extBtnPanel.add(sortNowBtn);

        extPanel.add(extBtnPanel, BorderLayout.SOUTH);

        dialog.add(extPanel, BorderLayout.CENTER);

        Runnable refreshExtList = () -> {
            String selectedFolder = folderList.getSelectedValue();
            extModel.clear();
            if (selectedFolder == null) return;

            java.util.List<String> list = customCategories.getOrDefault(
                    selectedFolder, new java.util.ArrayList<>()
            );
            for (String ext : list) {
                extModel.addElement(ext);
            }
        };

        if (!folderModel.isEmpty()) {
            folderList.setSelectedIndex(0);
            refreshExtList.run();
        }

        folderList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshExtList.run();
            }
        });

        addFolderBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(dialog,
                    "Enter folder name (category):", "New Folder",
                    JOptionPane.PLAIN_MESSAGE);
            if (name == null) return; 
            name = name.trim();
            if (name.isEmpty()) return;

            if (!customCategories.containsKey(name)) {
                customCategories.put(name, new java.util.ArrayList<>());
                folderModel.addElement(name);
            }
        });

        removeFolderBtn.addActionListener(e -> {
            String selectedFolder = folderList.getSelectedValue();
            if (selectedFolder == null) {
                JOptionPane.showMessageDialog(dialog, "Select a folder to remove.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (selectedFolder.equals("Others")) {
                JOptionPane.showMessageDialog(dialog, "\"Others\" cannot be removed.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Remove folder \"" + selectedFolder + "\"?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                customCategories.remove(selectedFolder);
                folderModel.removeElement(selectedFolder);
                extModel.clear();
            }
        });

        renameFolderBtn.addActionListener(e -> {
            String selectedFolder = folderList.getSelectedValue();
            if (selectedFolder == null) {
                JOptionPane.showMessageDialog(dialog, "Select a folder to rename.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (selectedFolder.equals("Others")) {
                JOptionPane.showMessageDialog(dialog, "\"Others\" cannot be renamed.",
                        "Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String newName = JOptionPane.showInputDialog(dialog,
                    "New name for folder:", selectedFolder);
            if (newName == null) return;
            newName = newName.trim();
            if (newName.isEmpty()) return;

            if (customCategories.containsKey(newName)) {
                JOptionPane.showMessageDialog(dialog, "A folder with that name already exists.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            java.util.List<String> exts = customCategories.remove(selectedFolder);
            customCategories.put(newName, exts);

            int idx = folderList.getSelectedIndex();
            folderModel.set(idx, newName);
            folderList.setSelectedIndex(idx);
        });

        // + Ext
        addExtBtn.addActionListener(e -> {
            String folder = folderList.getSelectedValue();
            if (folder == null) {
                JOptionPane.showMessageDialog(dialog, "Select a folder first.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String ext = extField.getText().trim().toLowerCase();
            if (ext.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Enter an extension.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (ext.startsWith(".")) ext = ext.substring(1);

            java.util.List<String> list = customCategories.get(folder);
            if (list == null) {
                list = new java.util.ArrayList<>();
                customCategories.put(folder, list);
            }
            if (!list.contains(ext)) {
                list.add(ext);
                extModel.addElement(ext);
            }
            extField.setText("");
        });

        removeExtBtn.addActionListener(e -> {
            String folder = folderList.getSelectedValue();
            if (folder == null) {
                JOptionPane.showMessageDialog(dialog, "Select a folder.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            java.util.List<String> list = customCategories.get(folder);
            if (list == null) return;

            java.util.List<String> selectedExts = extList.getSelectedValuesList();
            if (selectedExts.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Select extensions to remove.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            list.removeAll(selectedExts);
            refreshExtList.run();
        });

        sortNowBtn.addActionListener(e -> {
            String source = downloadsField.getText().trim();
            String target = targetField.getText().trim();

            if (source.isEmpty() || target.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Please set Source and Destination in the main window.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Run sorter in background
            new Thread(() -> {
                try {
                    FileSorter sorter = new FileSorter(source, target, customCategories, separateByExtension);
                    sorter.sortFiles(System.out::println);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(dialog,
                                    "Sorting completed.\nCheck terminal for details.",
                                    "Info", JOptionPane.INFORMATION_MESSAGE)
                    );
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(dialog,
                                    "Sorting failed:\n" + ex.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE)
                    );
                }
            }).start();
        });

        dialog.setVisible(true);
    }

    private void chooseFolder(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void handleSortOnce(ActionEvent e) {
        String source = downloadsField.getText().trim();
        String target = targetField.getText().trim();

        if (source.isEmpty() || target.isEmpty()) {
            showError("Please select both source and target folders.");
            return;
        }

        // sorting in a background thread 
        new Thread(() -> {
            try {
                FileSorter sorter = new FileSorter(source, target, customCategories, separateByExtension);
                int[] result = sorter.sortFiles(System.out::println);
                final int totalFiles = result[0];
                final int movedFiles = result[1];
                SwingUtilities.invokeLater(() ->
                        showInfo("Sorting completed.\nTotal files found: " + totalFiles + "\nFiles moved: " + movedFiles)
                );
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        showError("Sorting failed:\n" + ex.getMessage())
                );
            }
        }).start();
    }

    private void handleStartAuto(ActionEvent e) {
        if (scheduler != null && !scheduler.isShutdown()) {
            showInfo("Auto-sort is already running.");
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(intervalField.getText().trim());
            if (minutes <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError("Invalid interval. Enter a positive integer (minutes).");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String currentSource = downloadsField.getText().trim();
                String currentTarget = targetField.getText().trim();

                if (currentSource.isEmpty() || currentTarget.isEmpty()) {
                    System.err.println("Auto-sort skipped: source/target not set.");
                    return;
                }

                FileSorter sorter = new FileSorter(currentSource, currentTarget, customCategories, separateByExtension);
                int[] result = sorter.sortFiles(System.out::println);
                System.out.println("Auto-sort run completed. Moved " + result[1] + " of " + result[0]);
            } catch (Exception ex) {
                System.err.println("Auto-sort run failed: " + ex.getMessage());
            }
        }, 0, minutes, TimeUnit.MINUTES);

        showInfo("Auto-sort started. Running every " + minutes + " minute(s).");
    }

    private void stopAutoSort() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            showInfo("Auto-sort stopped.");
        } else {
            showInfo("Auto-sort is not running.");
        }
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(
                this,
                msg,
                "Info",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(
                this,
                msg,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FileSorterGUI().setVisible(true));
    }
}

// main
class FileSorter {
    private final Path downloadsPath;
    private final Path targetBasePath;
    private final Map<String, java.util.List<String>> categories = new java.util.HashMap<>();
    private final Map<String, Integer> stats = new java.util.HashMap<>();
    private final boolean separateByExtension;

    public FileSorter(String downloadsPath,
                      String targetBasePath,
                      Map<String, java.util.List<String>> customCategories,
                      boolean separateByExtension) {
        this.downloadsPath = Paths.get(downloadsPath);
        this.targetBasePath = Paths.get(targetBasePath);
        this.separateByExtension = separateByExtension;
        initializeCategories(customCategories);
    }

    public FileSorter(String downloadsPath, String targetBasePath) {
        this(downloadsPath, targetBasePath, null, false);
    }

    private void initializeCategories(Map<String, java.util.List<String>> customCategories) {
        categories.clear();
        if (customCategories != null && !customCategories.isEmpty()) {
            for (Map.Entry<String, java.util.List<String>> e : customCategories.entrySet()) {
                categories.put(e.getKey(), new java.util.ArrayList<>(e.getValue()));
            }
        }

        categories.putIfAbsent("Others", new java.util.ArrayList<>());

        // reset stats
        stats.clear();
        for (String cat : categories.keySet()) {
            stats.put(cat, 0);
        }
    }


    private String getFileExtension(String filename) {
        if (filename == null) return null;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) return null;
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String getCategoryForFile(String filename) {
        String ext = getFileExtension(filename);
        if (ext == null) return "Others";

        for (Map.Entry<String, java.util.List<String>> entry : categories.entrySet()) {
            String category = entry.getKey();
            java.util.List<String> exts = entry.getValue();

            if (exts != null && exts.contains(ext)) {
                return category;
            }
        }

        return "Others";
    }

    private void moveFile(Path sourceFile, Path targetFile) throws IOException {
        Files.createDirectories(targetFile.getParent());
        Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    public int[] sortFiles(Consumer<String> logger) throws IOException {
        int totalFiles = 0;
        int movedFiles = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(downloadsPath)) {
            for (Path sourceFile : stream) {
                if (Files.isDirectory(sourceFile)) continue; // skip directories

                totalFiles++;

                String category = getCategoryForFile(sourceFile.getFileName().toString());
                Path targetDir = targetBasePath.resolve(category);
                Path targetFile;

                if (separateByExtension) {
                    String ext = getFileExtension(sourceFile.getFileName().toString());
                    targetFile = targetDir.resolve(ext + "/" + sourceFile.getFileName());
                } else {
                    targetFile = targetDir.resolve(sourceFile.getFileName());
                }

                try {
                    moveFile(sourceFile, targetFile);
                    movedFiles++;
                    stats.merge(category, 1, Integer::sum);
                    logger.accept("Moved: " + sourceFile + " -> " + targetFile);
                } catch (IOException e) {
                    logger.accept("Failed to move " + sourceFile + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IOException("Error reading source directory: " + e.getMessage(), e);
        }

        return new int[]{totalFiles, movedFiles};
    }

    public Map<String, Integer> getStats() {
        return stats;
    }
}
