package geminiclient.gemini.base;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.ShutdownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleManager;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.Minecraft;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration manager, responsible for saving and loading module
 * configurations
 * using the org.json library.
 */
public class FileSystem {
    private static final Logger LOGGER = Logger.getLogger(FileSystem.class.getName());
    private static final String DEFAULT_CONFIG_NAME = "config";
    private static final String CONFIG_EXTENSION = ".json";
    private static final String ALTS_CONFIG_NAME = "alts";

    private final ModuleManager moduleManager;
    private final Path configDirectory;
    private final Path configNameFile;
    private final Path altsFile;
    private final Path ttfDirectory;

    public FileSystem(ModuleManager moduleManager) {
        Gemini.eventManager.register(this);
        this.moduleManager = moduleManager;

        // Initialize paths using NIO for better path handling
        this.configDirectory = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "gemini", "configs");
        this.configNameFile = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "gemini", "configName.txt");
        this.altsFile = configDirectory.resolve("alts.json");
        this.ttfDirectory = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "gemini", "ttf");

        ensureDirectoriesExist();
    }

    // =========================================================================
    // Directory Management
    // =========================================================================

    /**
     * Ensures required directories exist
     */
    private void ensureDirectoriesExist() {
        ensureDirectory(configDirectory, "config");
        ensureDirectory(ttfDirectory, "TTF");
    }

    private boolean ensureDirectory(Path directory, String description) {
        try {
            if (Files.exists(directory) && !Files.isDirectory(directory)) {
                LOGGER.severe(description + " path exists but is not a directory: " + directory);
                return false;
            }

            Files.createDirectories(directory);
            LOGGER.info(description + " directory ensured: " + directory);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create " + description + " directory: " + directory, e);
            return false;
        }
    }

    private boolean ensureParentDirectory(Path file) {
        Path parent = file.getParent();
        return parent == null || ensureDirectory(parent, "parent");
    }

    private Optional<String> normalizeConfigName(String name) {
        if (name == null) {
            return Optional.empty();
        }

        String normalized = name.trim();
        if (normalized.toLowerCase(Locale.ROOT).endsWith(CONFIG_EXTENSION)) {
            normalized = normalized.substring(0, normalized.length() - CONFIG_EXTENSION.length()).trim();
        }

        if (normalized.isEmpty()
                || normalized.equals(".")
                || normalized.equals("..")
                || normalized.equalsIgnoreCase(ALTS_CONFIG_NAME)
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains(":")
                || normalized.indexOf('\0') >= 0) {
            return Optional.empty();
        }

        try {
            Paths.get(normalized);
            return Optional.of(normalized);
        } catch (InvalidPathException e) {
            LOGGER.log(Level.WARNING, "Invalid config name: " + name, e);
            return Optional.empty();
        }
    }

    private Optional<Path> resolveConfigFile(String name) {
        Optional<String> configName = normalizeConfigName(name);
        if (configName.isEmpty()) {
            return Optional.empty();
        }

        Path configRoot = configDirectory.toAbsolutePath().normalize();
        Path configFile = configRoot.resolve(configName.get() + CONFIG_EXTENSION).normalize();
        if (!configFile.startsWith(configRoot)) {
            return Optional.empty();
        }

        return Optional.of(configFile);
    }

    private boolean writeStringAtomically(Path file, String content) {
        if (!ensureParentDirectory(file)) {
            return false;
        }

        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write file: " + file, e);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException cleanupError) {
                LOGGER.log(Level.WARNING, "Failed to clean temporary file: " + tempFile, cleanupError);
            }
            return false;
        }
    }

    /**
     * Scans the gemini/ttf/ directory for .ttf files and returns their names
     * (without the .ttf extension).
     */
    public List<String> scanTtfFonts() {
        List<String> fonts = new ArrayList<>();
        try {
            File dir = ttfDirectory.toFile();
            if (!dir.exists() || !dir.isDirectory()) {
                return fonts;
            }
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".ttf"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    fonts.add(name.substring(0, name.length() - 4)); // strip .ttf
                }
            }
            LOGGER.info("Scanned TTF fonts: " + fonts);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to scan TTF fonts directory", e);
        }
        return fonts;
    }

    /**
     * Returns the File for a given TTF font name (without .ttf extension).
     * Returns null if the name is "Default" or blank.
     */
    public File getTtfFontFile(String name) {
        if (name == null || name.isEmpty() || "Default".equals(name)) {
            return null;
        }

        String fontName = name.trim();
        if (fontName.isEmpty()
                || fontName.contains("/")
                || fontName.contains("\\")
                || fontName.contains(":")
                || fontName.indexOf('\0') >= 0) {
            LOGGER.warning("Attempted to access TTF font with invalid name: " + name);
            return null;
        }

        Path fontRoot = ttfDirectory.toAbsolutePath().normalize();
        Path fontFile = fontRoot.resolve(fontName + ".ttf").normalize();
        if (!fontFile.startsWith(fontRoot)) {
            LOGGER.warning("Attempted to access TTF font outside directory: " + name);
            return null;
        }

        return fontFile.toFile();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Finds a module in the ModuleManager by name.
     */
    private Optional<Module> getModuleByName(String name) {
        return moduleManager.getModules().stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Finds a value within the specified module by name.
     */
    private Optional<ValueParent> getValueByName(Module module, String name) {
        return module.getValues().stream()
                .filter(v -> v.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    @SuppressWarnings("unused")
    @EventTarget
    public void shutdown(ShutdownEvent event) {
        saveConfig();
        saveConfigName();
        LOGGER.info("Shutdown completed, last config: " + Gemini.lastConfigName);
    }

    // =========================================================================
    // Config Name Management
    // =========================================================================

    /**
     * Loads the last used config name from file
     */
    public void loadConfigName() {
        try {
            if (!Files.exists(configNameFile)) {
                Gemini.lastConfigName = DEFAULT_CONFIG_NAME;
                saveConfigName();
                LOGGER.info("Created default config name file: " + configNameFile);
                return;
            }

            // Read the config name
            String configName = Files.readString(configNameFile, StandardCharsets.UTF_8).trim();
            Optional<String> normalizedName = normalizeConfigName(configName);
            if (normalizedName.isPresent()) {
                Gemini.lastConfigName = normalizedName.get();
                LOGGER.info("Loaded config name: " + Gemini.lastConfigName);
            } else {
                Gemini.lastConfigName = DEFAULT_CONFIG_NAME;
                saveConfigName();
                LOGGER.warning("Invalid saved config name, reset to default: " + configName);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config name from: " + configNameFile, e);
            Gemini.lastConfigName = DEFAULT_CONFIG_NAME; // Fallback
        }
    }

    /**
     * Saves the current config name to file
     */
    public void saveConfigName() {
        Optional<String> normalizedName = normalizeConfigName(Gemini.lastConfigName);
        if (normalizedName.isEmpty()) {
            Gemini.lastConfigName = DEFAULT_CONFIG_NAME;
            normalizedName = Optional.of(DEFAULT_CONFIG_NAME);
        }

        if (!writeStringAtomically(configNameFile, normalizedName.get())) {
            return;
        }

        Gemini.lastConfigName = normalizedName.get();
        LOGGER.info("Config name saved: " + Gemini.lastConfigName);
    }

    // =========================================================================
    // Save Config Methods
    // =========================================================================

    /**
     * Saves configuration using the last used config name
     */
    public void saveConfig() {
        saveConfig(Gemini.lastConfigName == null ? DEFAULT_CONFIG_NAME : Gemini.lastConfigName);
    }

    /**
     * Saves configuration with specified name
     */
    public void saveConfig(String name) {
        Optional<String> normalizedName = normalizeConfigName(name);
        Optional<Path> configPath = resolveConfigFile(name);
        if (normalizedName.isEmpty() || configPath.isEmpty()) {
            LOGGER.warning("Attempted to save config with invalid name: " + name);
            return;
        }

        Path configFile = configPath.get();
        JSONObject configRoot = buildConfigJson();
        String jsonContent = configRoot.toString(4);

        if (!writeStringAtomically(configFile, jsonContent)) {
            return;
        }

        Gemini.lastConfigName = normalizedName.get();
        saveConfigName();
        LOGGER.info("Configuration successfully saved: " + configFile);
    }

    /**
     * Creates a config file only when it does not already exist.
     */
    public boolean createConfig(String name) {
        Optional<String> normalizedName = normalizeConfigName(name);
        Optional<Path> configPath = resolveConfigFile(name);
        if (normalizedName.isEmpty() || configPath.isEmpty()) {
            LOGGER.warning("Attempted to create config with invalid name: " + name);
            return false;
        }

        Path configFile = configPath.get();
        if (Files.exists(configFile)) {
            LOGGER.warning("Config already exists: " + configFile);
            return false;
        }

        if (!writeStringAtomically(configFile, buildConfigJson().toString(4))) {
            return false;
        }

        Gemini.lastConfigName = normalizedName.get();
        saveConfigName();
        LOGGER.info("Configuration successfully created: " + configFile);
        return true;
    }

    /**
     * Deletes a saved config file without allowing paths outside the config directory.
     */
    public boolean deleteConfig(String name) {
        Optional<String> normalizedName = normalizeConfigName(name);
        Optional<Path> configPath = resolveConfigFile(name);
        if (normalizedName.isEmpty() || configPath.isEmpty()) {
            LOGGER.warning("Attempted to delete config with invalid name: " + name);
            return false;
        }

        Path configFile = configPath.get();
        try {
            boolean deleted = Files.deleteIfExists(configFile);
            if (deleted && normalizedName.get().equalsIgnoreCase(Gemini.lastConfigName)) {
                Gemini.lastConfigName = DEFAULT_CONFIG_NAME;
                saveConfigName();
            }
            LOGGER.info((deleted ? "Configuration deleted: " : "Configuration did not exist: ") + configFile);
            return deleted;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete configuration: " + configFile, e);
            return false;
        }
    }

    /**
     * Builds the complete configuration JSON structure
     */
    private JSONObject buildConfigJson() {
        JSONObject configRoot = new JSONObject();
        JSONArray modulesArray = new JSONArray();

        for (Module module : moduleManager.getModules()) {
            modulesArray.put(buildModuleJson(module));
        }

        configRoot.put("modules", modulesArray);
        return configRoot;
    }

    /**
     * Builds JSON representation of a single module
     */
    private JSONObject buildModuleJson(Module module) {
        JSONObject moduleObject = new JSONObject();

        // Basic Module Info
        moduleObject.put("name", module.getName());
        moduleObject.put("category", module.getModuleEnum().name());
        moduleObject.put("enabled", module.enabled);
        moduleObject.put("favorite", module.favorite);
        moduleObject.put("key", module.key);
        moduleObject.put("hudX", module.hudX);
        moduleObject.put("hudY", module.hudY);

        // Module Values
        JSONArray valuesArray = new JSONArray();
        for (ValueParent value : module.getValues()) {
            valuesArray.put(buildValueJson(value));
        }
        moduleObject.put("values", valuesArray);

        return moduleObject;
    }

    /**
     * Builds JSON representation of a single value
     */
    private JSONObject buildValueJson(ValueParent value) {
        JSONObject valueObject = new JSONObject();
        valueObject.put("name", value.getName());

        try {
            switch (value) {
                case BoolValue boolValue -> {
                    valueObject.put("type", "Bool");
                    valueObject.put("value", boolValue.enabled);
                }
                case IntValue intValue -> {
                    valueObject.put("type", "Int");
                    valueObject.put("value", intValue.getValue());
                }
                case FloatValue floatValue -> {
                    valueObject.put("type", "Float");
                    valueObject.put("value", floatValue.getValue());
                }
                case ListValue listValue -> {
                    valueObject.put("type", "List");
                    valueObject.put("value", listValue.get());
                }
                case IntRangeValue intRangeValue -> {
                    valueObject.put("type", "IntRange");
                    JSONObject range = new JSONObject();
                    range.put("min", intRangeValue.getMinValue());
                    range.put("max", intRangeValue.getMaxValue());
                    valueObject.put("value", range);
                }
                case FloatRangeValue floatRangeValue -> {
                    valueObject.put("type", "FloatRange");
                    JSONObject range = new JSONObject();
                    range.put("min", floatRangeValue.getMinValue());
                    range.put("max", floatRangeValue.getMaxValue());
                    valueObject.put("value", range);
                }
                case CheckboxValue checkboxValue -> {
                    valueObject.put("type", "Checkbox");
                    JSONArray boolsArray = new JSONArray();
                    for (BoolValue boolValue : checkboxValue.boolValues) {
                        JSONObject boolObject = new JSONObject();
                        boolObject.put("name", boolValue.getName());
                        boolObject.put("enabled", boolValue.enabled);
                        boolsArray.put(boolObject);
                    }
                    valueObject.put("value", boolsArray);
                }
                case ColorValue colorValue -> {
                    valueObject.put("type", "Color");
                    valueObject.put("value", colorValue.getColor());
                }
                default -> LOGGER.warning("Unhandled value type: " + value.getClass().getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to serialize value: " + value.getName(), e);
        }

        return valueObject;
    }

    // =========================================================================
    // Load Config Methods
    // =========================================================================

    /**
     * Loads configuration using the last used config name
     */
    public void loadConfig() {
        loadConfig(Gemini.lastConfigName == null ? DEFAULT_CONFIG_NAME : Gemini.lastConfigName);
    }

    /**
     * Loads configuration with specified name
     */
    public void loadConfig(String name) {
        Optional<String> normalizedName = normalizeConfigName(name);
        Optional<Path> configPath = resolveConfigFile(name);
        if (normalizedName.isEmpty() || configPath.isEmpty()) {
            LOGGER.warning("Attempted to load config with invalid name: " + name);
            return;
        }

        Path configFile = configPath.get();
        LOGGER.info("Loading configuration from: " + configFile);

        if (!Files.exists(configFile)) {
            LOGGER.warning("Config file does not exist: " + configFile);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(configFile)) {
            JSONObject configRoot = new JSONObject(new JSONTokener(inputStream));
            applyConfigFromJson(configRoot);
            Gemini.lastConfigName = normalizedName.get();
            saveConfigName();
            LOGGER.info("Configuration loaded successfully: " + configFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read configuration file: " + configFile, e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "JSON parsing error in configuration file: " + configFile, e);
        }
    }

    /**
     * Applies configuration from JSON object to modules
     */
    private void applyConfigFromJson(JSONObject configRoot) {
        if (!configRoot.has("modules")) {
            LOGGER.warning("Missing 'modules' array in configuration");
            return;
        }

        // 加载配置时抑制 IPC 回调，避免模块/值变更时误发增量到 C#
        try {
            JSONArray modulesArray = configRoot.getJSONArray("modules");
            int loadedCount = 0;

            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject moduleJson = modulesArray.getJSONObject(i);
                if (applyModuleConfig(moduleJson)) {
                    loadedCount++;
                }
            }

            LOGGER.info("Applied configuration to " + loadedCount + " modules");
        } finally {
        }
    }

    /**
     * Applies configuration to a single module
     */
    private boolean applyModuleConfig(JSONObject moduleJson) {
        String moduleName = moduleJson.getString("name");
        Optional<Module> moduleOpt = getModuleByName(moduleName);

        if (moduleOpt.isEmpty()) {
            LOGGER.warning("Module not found: " + moduleName);
            return false;
        }

        Module module = moduleOpt.get();

        // Apply basic module settings
        if (moduleJson.has("enabled")) {
            module.setEnabled(moduleJson.getBoolean("enabled"));
        }
        if (moduleJson.has("key")) {
            module.key = moduleJson.getInt("key");
        }
        module.favorite = moduleJson.optBoolean("favorite", false);
        module.hudX = moduleJson.optInt("hudX", 6);
        module.hudY = moduleJson.optInt("hudY", 6);

        // Apply module values
        if (moduleJson.has("values")) {
            applyModuleValues(module, moduleJson.getJSONArray("values"));
        }

        return true;
    }

    /**
     * Applies values configuration to a module
     */
    private void applyModuleValues(Module module, JSONArray valuesArray) {
        int appliedCount = 0;

        for (int j = 0; j < valuesArray.length(); j++) {
            JSONObject valueJson = valuesArray.getJSONObject(j);
            if (applyValueConfig(module, valueJson)) {
                appliedCount++;
            }
        }

        LOGGER.fine("Applied " + appliedCount + " values to module: " + module.getName());
    }

    /**
     * Applies configuration to a single value
     */
    private boolean applyValueConfig(Module module, JSONObject valueJson) {
        String valueName = valueJson.getString("name");
        String valueType = valueJson.optString("type", "");

        if (valueType.isEmpty()) {
            LOGGER.warning("Missing type for value " + valueName + " in module " + module.getName());
            return false;
        }

        Optional<ValueParent> valueOpt = getValueByName(module, valueName);
        if (valueOpt.isEmpty()) {
            LOGGER.warning("Value not found in module " + module.getName() + ": " + valueName);
            return false;
        }

        ValueParent vp = valueOpt.get();
        if (!isTypeMatch(vp, valueType)) {
            LOGGER.warning("Type mismatch for " + valueName + ": config has " + valueType
                    + " but module has " + vp.getClass().getSimpleName() + ", skipping");
            return false;
        }

        if (valueJson.has("value")) {
            try {
                applyValue(vp, valueType, valueJson.get("value"));
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Failed to apply value " + valueName + " in module " + module.getName(), e);
            }
        }

        return false;
    }

    private static boolean isTypeMatch(ValueParent value, String type) {
        return switch (type) {
            case "Bool" -> value instanceof BoolValue;
            case "Int" -> value instanceof IntValue;
            case "Float" -> value instanceof FloatValue;
            case "List" -> value instanceof ListValue;
            case "IntRange" -> value instanceof IntRangeValue;
            case "FloatRange" -> value instanceof FloatRangeValue;
            case "Checkbox" -> value instanceof CheckboxValue;
            case "Color" -> value instanceof ColorValue;
            default -> false;
        };
    }

    /**
     * Applies the JSON value to the ValueParent instance based on the type.
     */
    private void applyValue(ValueParent value, String type, Object jsonValue) {
        switch (type) {
            case "Bool" -> ((BoolValue) value).enabled = (boolean) jsonValue;
            case "Int" -> ((IntValue) value).setValue((int) jsonValue);
            case "Float" -> ((FloatValue) value).setValue(((Number) jsonValue).floatValue());
            case "List" -> ((ListValue) value).setMode((String) jsonValue);
            case "IntRange" -> applyIntRangeValue((IntRangeValue) value, (JSONObject) jsonValue);
            case "FloatRange" -> applyFloatRangeValue((FloatRangeValue) value, (JSONObject) jsonValue);
            case "Checkbox" -> applyCheckboxValue((CheckboxValue) value, (JSONArray) jsonValue);
            case "Color" -> ((ColorValue) value).setColor(((Number) jsonValue).intValue());
            default -> LOGGER.warning("Unknown value type: " + type);
        }
    }

    /**
     * Applies integer range value configuration
     */
    private void applyIntRangeValue(IntRangeValue value, JSONObject range) {
        // Set max first to avoid constraint errors
        value.setMaxValue(range.getInt("max"));
        value.setMinValue(range.getInt("min"));
    }

    /**
     * Applies float range value configuration
     */
    private void applyFloatRangeValue(FloatRangeValue value, JSONObject range) {
        // Set max first to avoid constraint errors
        value.setMaxValue((float) range.getDouble("max"));
        value.setMinValue((float) range.getDouble("min"));
    }

    /**
     * Applies checkbox value configuration
     */
    private void applyCheckboxValue(CheckboxValue checkboxValue, JSONArray boolsArray) {
        for (int i = 0; i < boolsArray.length(); i++) {
            JSONObject boolJson = boolsArray.getJSONObject(i);
            String boolName = boolJson.getString("name");
            boolean boolState = boolJson.getBoolean("enabled");

            for (BoolValue boolValue : checkboxValue.boolValues) {
                if (boolValue.getName().equalsIgnoreCase(boolName)) {
                    boolValue.enabled = boolState;
                    break;
                }
            }
        }
    }

    /**
     * 读取本地保存的账号列表，如果不存在则返回一个空的 JSONArray
     */
    public JSONArray loadAlts() {
        if (!Files.exists(altsFile)) {
            return new JSONArray();
        }
        try (InputStream inputStream = Files.newInputStream(altsFile)) {
            return new JSONArray(new JSONTokener(inputStream));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load alts from: " + altsFile, e);
            return new JSONArray();
        }
    }

    /**
     * 将当前的账号列表保存到 alts.json
     */
    public void saveAlts(JSONArray accounts) {
        if (accounts == null) {
            LOGGER.warning("Attempted to save null alts list");
            return;
        }

        if (writeStringAtomically(altsFile, accounts.toString(4))) {
            LOGGER.info("Alts successfully saved: " + altsFile);
        }
    }
}
