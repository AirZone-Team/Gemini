package geminiclient.gemini.base;

import net.minecraft.client.Minecraft;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages custom background configuration for the main menu.
 * Supports multiple backgrounds with cycling functionality.
 * Automatically detects all image files (PNG, JPG, JPEG) in config/gemini directory.
 */
public class BackgroundConfig {
    private static final Logger LOGGER = Logger.getLogger(BackgroundConfig.class.getName());
    private static final String CONFIG_FILE_NAME = "background.json";
    private static final String[] SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg"};

    private final Path configDirectory;
    private final Path configFile;

    private boolean customBackgroundEnabled = false;
    private boolean particlesEnabled = true; // Particles enabled by default
    private int currentBackgroundIndex = 0;
    private List<Path> availableBackgrounds = new ArrayList<>();

    public BackgroundConfig() {
        this.configDirectory = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "config", "gemini");
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);

        ensureDirectoryExists();
        scanBackgrounds();
        load();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(configDirectory)) {
                Files.createDirectories(configDirectory);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create config directory: " + configDirectory, e);
        }
    }

    /**
     * Scans the config directory for all supported image files.
     */
    private void scanBackgrounds() {
        availableBackgrounds.clear();
        try (Stream<Path> files = Files.list(configDirectory)) {
            availableBackgrounds = files
                .filter(Files::isRegularFile)
                .filter(this::isSupportedImageFile)
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan backgrounds in: " + configDirectory, e);
        }
    }

    /**
     * Checks if a file has a supported image extension.
     */
    private boolean isSupportedImageFile(Path file) {
        String filename = file.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (filename.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads the background configuration from file.
     */
    public void load() {
        if (!Files.exists(configFile)) {
            save();
            return;
        }

        try (InputStream inputStream = Files.newInputStream(configFile)) {
            JSONObject json = new JSONObject(new JSONTokener(inputStream));
            customBackgroundEnabled = json.optBoolean("customBackgroundEnabled", false);
            particlesEnabled = json.optBoolean("particlesEnabled", true);
            currentBackgroundIndex = json.optInt("currentBackgroundIndex", 0);

            // Validate index
            if (currentBackgroundIndex < 0 || currentBackgroundIndex >= availableBackgrounds.size()) {
                currentBackgroundIndex = 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load background config from: " + configFile, e);
            customBackgroundEnabled = false;
            particlesEnabled = true;
            currentBackgroundIndex = 0;
        }
    }

    /**
     * Saves the current background configuration to file.
     */
    public void save() {
        try {
            JSONObject json = new JSONObject();
            json.put("customBackgroundEnabled", customBackgroundEnabled);
            json.put("particlesEnabled", particlesEnabled);
            json.put("currentBackgroundIndex", currentBackgroundIndex);

            Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(tempFile, json.toString(4), StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save background config to: " + configFile, e);
        }
    }

    /**
     * Checks if custom background is enabled.
     */
    public boolean isCustomBackgroundEnabled() {
        return customBackgroundEnabled;
    }

    /**
     * Sets whether custom background is enabled.
     */
    public void setCustomBackgroundEnabled(boolean enabled) {
        this.customBackgroundEnabled = enabled;
        save();
    }

    /**
     * Toggles the custom background on/off.
     */
    public void toggle() {
        customBackgroundEnabled = !customBackgroundEnabled;
        save();
    }

    /**
     * Checks if particles are enabled.
     */
    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    /**
     * Sets whether particles are enabled.
     */
    public void setParticlesEnabled(boolean enabled) {
        this.particlesEnabled = enabled;
        save();
    }

    /**
     * Toggles particles on/off.
     */
    public void toggleParticles() {
        particlesEnabled = !particlesEnabled;
        save();
    }

    /**
     * Cycles to the next background image.
     * Rescans directory to pick up new files.
     */
    public void nextBackground() {
        scanBackgrounds();
        if (availableBackgrounds.isEmpty()) {
            currentBackgroundIndex = 0;
            return;
        }

        currentBackgroundIndex = (currentBackgroundIndex + 1) % availableBackgrounds.size();
        save();
    }

    /**
     * Sets the selected wallpaper by file path.
     * Rescans and updates the current index.
     */
    public void setSelectedWallpaper(Path wallpaperPath) {
        scanBackgrounds();
        for (int i = 0; i < availableBackgrounds.size(); i++) {
            if (availableBackgrounds.get(i).equals(wallpaperPath)) {
                currentBackgroundIndex = i;
                save();
                return;
            }
        }
        // If not found, default to first
        currentBackgroundIndex = 0;
        save();
    }

    /**
     * Gets the number of available backgrounds.
     */
    public int getBackgroundCount() {
        return availableBackgrounds.size();
    }

    /**
     * Gets the current background index (1-based for display).
     */
    public int getCurrentBackgroundNumber() {
        return availableBackgrounds.isEmpty() ? 0 : currentBackgroundIndex + 1;
    }

    /**
     * Checks if any custom background files exist.
     */
    public boolean customBackgroundFileExists() {
        return !availableBackgrounds.isEmpty();
    }

    /**
     * Gets the backgrounds directory path.
     */
    public Path getBackgroundsDirectory() {
        return configDirectory;
    }

    /**
     * Gets the path to the current custom background file.
     */
    public Path getCustomBackgroundFile() {
        if (availableBackgrounds.isEmpty()) {
            return configDirectory.resolve("custom_bg.png");
        }
        if (currentBackgroundIndex < 0 || currentBackgroundIndex >= availableBackgrounds.size()) {
            currentBackgroundIndex = 0;
        }
        return availableBackgrounds.get(currentBackgroundIndex);
    }

    /**
     * Gets the config directory path.
     */
    public Path getConfigDirectory() {
        return configDirectory;
    }
}
