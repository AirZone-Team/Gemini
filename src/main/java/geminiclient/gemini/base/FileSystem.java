package geminiclient.gemini.base;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.events.impl.ShutdownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleManager;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;

import net.minecraft.client.Minecraft;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.util.List;
import java.util.Optional;

/**
 * Configuration manager, responsible for saving and loading module configurations
 * using the org.json library.
 */
public class FileSystem {

    private final ModuleManager moduleManager;
    // Configuration file path. In a real application, ensure the file is readable and writable.
    private static final String CONFIG_FILE_PATH = Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configs\\";

    public FileSystem(ModuleManager moduleManager) {
        Gemini.eventManager.register(this);
        this.moduleManager = moduleManager;
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

    @SuppressWarnings("unused")
    @EventTarget
    public void shutdown(ShutdownEvent event) {
        saveConfig();
        saveConfigName();
        System.out.println(Gemini.lastConfigName);
    }

    public void loadConfigName() {
        File file = new File(Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configName.txt");
        if (!file.exists()) {
            try {
                file.createNewFile();
                try (FileWriter fileWriter = new FileWriter(file)) {
                    fileWriter.write("config");
                    fileWriter.flush();
                    System.out.println("Config name successfully written to file: " + Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configName.txt");
                }
                Gemini.lastConfigName = "config";
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                String data;
                while ((data = fileReader.readLine()) != null) {
                    System.out.println(data);
                    Gemini.lastConfigName = data;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveConfigName() {
        if (!Gemini.lastConfigName.isEmpty()) {
            File file = new File(Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configName.txt");
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.write(Gemini.lastConfigName);
                fileWriter.flush();
                System.out.println("Config name successfully written to file: " + Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configName.txt");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ---

    // =========================================================================
    // Save Config Methods
    // =========================================================================

    /**
     * Iterates over all modules and their values, converts the configuration to a
     * JSONObject, and simulates saving it to a file.
     */
    public void saveConfig() {
        this.saveConfig(Gemini.lastConfigName);
    }

    public void saveConfig(String name) {
        JSONObject configRoot = new JSONObject();
        JSONArray modulesArray = new JSONArray();
        File configFile = new File(CONFIG_FILE_PATH + name + ".json");

        List<Module> modules = moduleManager.getModules();

        for (Module module : modules) {
            JSONObject moduleObject = new JSONObject();

            // Basic Module Info
            moduleObject.put("name", module.getName());
            moduleObject.put("category", module.getModuleEnum().name());
            moduleObject.put("enabled", module.enabled); // Is the module enabled
            moduleObject.put("key", module.key); // Module keybind

            // Module Value Settings (Values)
            JSONArray valuesArray = new JSONArray();

            for (ValueParent value : module.getValues()) {
                JSONObject valueObject = new JSONObject();
                String valueName = value.getName();

                valueObject.put("name", valueName);

                // Check the specific value type and extract data
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
                        valueObject.put("value", listValue.get()); // Current selected mode
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
                    default -> {
                    }
                }

                valuesArray.put(valueObject);
            }

            moduleObject.put("values", valuesArray);
            modulesArray.put(moduleObject);
        }

        configRoot.put("modules", modulesArray);

        // --- Simulate saving to file ---
        System.out.println("--- Attempting to save configuration to " + configFile.getAbsolutePath() + " ---");
//        System.out.println(configRoot.toString(4));
        System.out.println("--- Configuration save complete ---");
        // --- Real file writing code (requires IO exception handling) follows: ---
        try {
            // 1. Check and create parent directory (if path is "configs/config.json")
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                // If the parent directory doesn't exist, try to create it
                if (parentDir.mkdirs()) {
                    System.out.println("Created config directory: " + parentDir.getAbsolutePath());
                } else {
                    System.err.println("Error: Failed to create config directory.");
                    return;
                }
            }

            // 2. Check and create file (if file doesn't exist)
            if (!configFile.exists()) {
                if (configFile.createNewFile()) {
                    System.out.println("Created new config file: " + configFile.getAbsolutePath());
                } else {
                    System.err.println("Error: Failed to create config file.");
                    return;
                }
            }

            // 3. Write config content
            try (FileWriter fileWriter = new FileWriter(configFile)) {
                fileWriter.write(configRoot.toString(4));
                fileWriter.flush();
                System.out.println("Configuration successfully written to file: " + configFile.getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("Error: Failed to save configuration (IO Exception). Please check file permissions and path.");
            e.printStackTrace();
        }
    }

    // ---

    // =========================================================================
    // Load Config Methods
    // =========================================================================

    /**
     * Simulates reading JSON configuration from a file and applying it to modules and values.
     */
    public void loadConfig() {
        this.loadConfig(Gemini.lastConfigName);
    }

    public void loadConfig(String name) {
        System.out.println("--- Starting configuration load (reading from file) ---");

        File configFile = new File(CONFIG_FILE_PATH + name + ".json");

        if (!configFile.exists()) {
            System.out.printf("Warning: Config file '%s' does not exist, skipping load.\n", configFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            // 1. Parse JSON from the BufferedReader stream using JSONTokener
            JSONObject configRoot = new JSONObject(new JSONTokener(reader));

            if (!configRoot.has("modules")) {
                System.err.println("Error: Missing 'modules' array in JSON root object.");
                return;
            }

            JSONArray modulesArray = configRoot.getJSONArray("modules");

            // 2. Iterate over module configurations in JSON
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject moduleJson = modulesArray.getJSONObject(i);
                String moduleName = moduleJson.getString("name");

                Optional<Module> moduleOpt = getModuleByName(moduleName);
                if (moduleOpt.isEmpty()) {
                    System.err.printf("Warning: Module named '%s' not found, skipping load.\n", moduleName);
                    continue;
                }
                Module module = moduleOpt.get();

                // 3. Set module state
                if (moduleJson.has("enabled")) {
                    module.setEnabledSilently(moduleJson.getBoolean("enabled"));
                }
                if (moduleJson.has("key")) {
                    module.key = moduleJson.getInt("key");
                }

                System.out.printf("  [Module] %s -> Basic state set\n", moduleName);

                // 4. Iterate over value configurations in the module
                if (moduleJson.has("values")) {
                    JSONArray valuesArray = moduleJson.getJSONArray("values");
                    for (int j = 0; j < valuesArray.length(); j++) {
                        JSONObject valueJson = valuesArray.getJSONObject(j);
                        String valueName = valueJson.getString("name");
                        String valueType = valueJson.getString("type");

                        Optional<ValueParent> valueOpt = getValueByName(module, valueName);
                        if (valueOpt.isEmpty()) {
                            System.err.printf("  Warning: Value '%s' not found in module %s, skipping load.\n", moduleName, valueName);
                            continue;
                        }

                        // 5. Apply value
                        if (valueJson.has("value")) {
                            applyValue(valueOpt.get(), valueType, valueJson.get("value"));
                            System.out.printf("    [Value] %s (%s) -> Set\n", valueName, valueType);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error: Failed to read configuration file.");
            e.printStackTrace();
        } catch (Exception e) {
            // Catch JSON parsing errors or structure errors
            System.err.println("Error: JSON format/structure exception occurred during config load:");
            e.printStackTrace();
        }

        System.out.println("--- Configuration load complete ---");
    }

    /**
     * Applies the JSON value to the ValueParent instance based on the type.
     * @param value The target ValueParent instance
     * @param type The type string recorded in the JSON
     * @param jsonValue The value object from JSON (can be boolean, int, float, String, JSONObject)
     */
    private void applyValue(ValueParent value, String type, Object jsonValue) {
        try {
            switch (type) {
                case "Bool":
                    ((BoolValue) value).enabled = (boolean) jsonValue;
                    break;
                case "Int":
                    ((IntValue) value).setValue((int) jsonValue);
                    break;
                case "Float":
                    // JSON parses all numbers as Number (usually Double or Integer)
                    ((FloatValue) value).setValue(((Number) jsonValue).floatValue());
                    break;
                case "List":
                    ((ListValue) value).setMode((String) jsonValue);
                    break;
                case "IntRange":
                    JSONObject intRange = (JSONObject) jsonValue;
                    IntRangeValue irValue = (IntRangeValue) value;
                    // Set MaxValue first, then MinValue, to avoid range constraint errors
                    irValue.setMaxValue(intRange.getInt("max"));
                    irValue.setMinValue(intRange.getInt("min"));
                    break;
                case "FloatRange":
                    JSONObject floatRange = (JSONObject) jsonValue;
                    FloatRangeValue frValue = (FloatRangeValue) value;
                    // Set MaxValue first, then MinValue
                    frValue.setMaxValue((float) floatRange.getDouble("max"));
                    frValue.setMinValue((float) floatRange.getDouble("min"));
                    break;
                default:
                    System.err.println("  Warning: Unknown value type: " + type);
                    break;
            }
        } catch (ClassCastException | NullPointerException e) {
            System.err.printf("  Warning: Type conversion error occurred while setting value '%s'. Configuration does not match code.\n", value.getName());
        }
    }
}