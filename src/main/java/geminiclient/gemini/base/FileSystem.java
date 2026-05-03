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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final ModuleManager moduleManager;
    private final Path configDirectory;
    private final Path configNameFile;
    private final Path altsFile;

    public FileSystem(ModuleManager moduleManager) {
        Gemini.eventManager.register(this);
        this.moduleManager = moduleManager;

        // Initialize paths using NIO for better path handling
        this.configDirectory = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "gemini", "configs");
        this.configNameFile = Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "gemini", "configName.txt");
        this.altsFile = configDirectory.resolve("alts.json");

        ensureDirectoriesExist();
    }

    // =========================================================================
    // Directory Management
    // =========================================================================

    /**
     * Ensures required directories exist
     */
    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(configDirectory);
            LOGGER.info("Config directory ensured: " + configDirectory);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create config directory: " + configDirectory, e);
        }
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
        JavaToCSharpIPC.shutdown();
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
                // Create file with default name if it doesn't exist
                Files.writeString(configNameFile, "config");
                Gemini.lastConfigName = "config";
                LOGGER.info("Created default config name file: " + configNameFile);
                return;
            }

            // Read the config name
            String configName = Files.readString(configNameFile).trim();
            if (!configName.isEmpty()) {
                Gemini.lastConfigName = configName;
                LOGGER.info("Loaded config name: " + configName);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config name from: " + configNameFile, e);
            Gemini.lastConfigName = "config"; // Fallback
        }
    }

    /**
     * Saves the current config name to file
     */
    public void saveConfigName() {
        if (Gemini.lastConfigName == null || Gemini.lastConfigName.isEmpty()) {
            return;
        }

        try {
            Files.writeString(configNameFile, Gemini.lastConfigName);
            LOGGER.info("Config name saved: " + Gemini.lastConfigName);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save config name to: " + configNameFile, e);
        }
    }

    // =========================================================================
    // Save Config Methods
    // =========================================================================

    /**
     * Saves configuration using the last used config name
     */
    public void saveConfig() {
        saveConfig(Gemini.lastConfigName);
    }

    /**
     * Saves configuration with specified name
     */
    public void saveConfig(String name) {
        if (name == null || name.trim().isEmpty()) {
            LOGGER.warning("Attempted to save config with invalid name: " + name);
            return;
        }

        Path configFile = configDirectory.resolve(name + ".json");
        JSONObject configRoot = buildConfigJson();

        try {
            // Write with pretty printing and UTF-8 encoding
            String jsonContent = configRoot.toString(4);
            Files.writeString(configFile, jsonContent);
            LOGGER.info("Configuration successfully saved: " + configFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save configuration: " + configFile, e);
        }
        Gemini.lastConfigName = name;
        loadConfig(Gemini.lastConfigName);
        LOGGER.info("Configuration successfully loaded: " + configFile);
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
        moduleObject.put("key", module.key);

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
        loadConfig(Gemini.lastConfigName);
    }

    /**
     * Loads configuration with specified name
     */
    public void loadConfig(String name) {
        if (name == null || name.trim().isEmpty()) {
            LOGGER.warning("Attempted to load config with invalid name: " + name);
            return;
        }

        Path configFile = Path.of(configDirectory + File.separator + name + ".json");
        LOGGER.info("Loading configuration from: " + configFile);

        if (!Files.exists(configFile)) {
            LOGGER.warning("Config file does not exist: " + configFile);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(configFile)) {
            JSONObject configRoot = new JSONObject(new JSONTokener(inputStream));
            applyConfigFromJson(configRoot);
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

        JSONArray modulesArray = configRoot.getJSONArray("modules");
        int loadedCount = 0;

        for (int i = 0; i < modulesArray.length(); i++) {
            JSONObject moduleJson = modulesArray.getJSONObject(i);
            if (applyModuleConfig(moduleJson)) {
                loadedCount++;
            }
        }

        LOGGER.info("Applied configuration to " + loadedCount + " modules");
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
        String valueType = valueJson.getString("type");

        Optional<ValueParent> valueOpt = getValueByName(module, valueName);
        if (valueOpt.isEmpty()) {
            LOGGER.warning("Value not found in module " + module.getName() + ": " + valueName);
            return false;
        }

        if (valueJson.has("value")) {
            try {
                applyValue(valueOpt.get(), valueType, valueJson.get("value"));
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Failed to apply value " + valueName + " in module " + module.getName(), e);
            }
        }

        return false;
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
        try {
            Files.writeString(altsFile, accounts.toString(4));
            LOGGER.info("Alts successfully saved: " + altsFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save alts: " + altsFile, e);
        }
    }
}