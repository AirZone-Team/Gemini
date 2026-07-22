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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages custom background configuration for the main menu.
 * Allows users to toggle between default and custom backgrounds.
 * Supports PNG, JPG, and JPEG formats.
 */
public class BackgroundConfig {
    private static final Logger LOGGER = Logger.getLogger(BackgroundConfig.class.getName());
    private static final String CONFIG_FILE_NAME = "background.json";
    private static final String[] SUPPORTED_EXTENSIONS = {"custom_bg.png", "custom_bg.jpg", "custom_bg.jpeg"};

    private final Path configDirectory;
    private final Path configFile;

    private boolean customBackgroundEnabled = false;

    public BackgroundConfig() {
        this.configDirectory = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "config", "gemini");
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);

        ensureDirectoryExists();
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
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load background config from: " + configFile, e);
            customBackgroundEnabled = false;
        }
    }

    /**
     * Saves the current background configuration to file.
     */
    public void save() {
        try {
            JSONObject json = new JSONObject();
            json.put("customBackgroundEnabled", customBackgroundEnabled);

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
     * Checks if the custom background image file exists.
     * Checks for .png, .jpg, and .jpeg extensions.
     */
    public boolean customBackgroundFileExists() {
        for (String filename : SUPPORTED_EXTENSIONS) {
            if (Files.exists(configDirectory.resolve(filename))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the path to the custom background file.
     * Returns the first found file from supported formats.
     */
    public Path getCustomBackgroundFile() {
        for (String filename : SUPPORTED_EXTENSIONS) {
            Path file = configDirectory.resolve(filename);
            if (Files.exists(file)) {
                return file;
            }
        }
        // Return default .png path even if it doesn't exist
        return configDirectory.resolve(SUPPORTED_EXTENSIONS[0]);
    }

    /**
     * Gets the config directory path.
     */
    public Path getConfigDirectory() {
        return configDirectory;
    }
}
