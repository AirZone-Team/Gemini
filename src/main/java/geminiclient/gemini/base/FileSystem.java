package geminiclient.gemini.base;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.ShutdownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleManager;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.Minecraft;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Manages Gemini configuration, account and font files.
 *
 * <p>All externally supplied names are validated before path resolution, and
 * writes are performed through a temporary file in the target directory.</p>
 */
public final class FileSystem {
    private static final Logger LOGGER = Logger.getLogger(FileSystem.class.getName());

    private static final String DEFAULT_CONFIG_NAME = "config";
    private static final String CONFIG_EXTENSION = ".json";
    private static final String ALTS_CONFIG_NAME = "alts";
    private static final String DEFAULT_FONT_NAME = "Default";
    private static final String TTF_EXTENSION = ".ttf";

    private static final String JSON_MODULES = "modules";
    private static final String JSON_VALUES = "values";
    private static final String JSON_NAME = "name";
    private static final String JSON_TYPE = "type";
    private static final String JSON_VALUE = "value";

    private final ModuleManager moduleManager;
    private final Path configDirectory;
    private final Path configNameFile;
    private final Path altsFile;
    private final Path ttfDirectory;

    /** Case-insensitive module lookup, rebuilt before loading a configuration. */
    private Map<String, Module> modulesByName = Map.of();

    public FileSystem(ModuleManager moduleManager) {
        this.moduleManager = java.util.Objects.requireNonNull(moduleManager, "moduleManager");
        Gemini.eventManager.register(this);

        Path geminiDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("gemini");
        this.configDirectory = geminiDirectory.resolve("configs");
        this.configNameFile = geminiDirectory.resolve("configName.txt");
        this.altsFile = configDirectory.resolve("alts.json");
        this.ttfDirectory = geminiDirectory.resolve("ttf");

        ensureDirectoriesExist();
        rebuildModuleIndex();
    }

    // -------------------------------------------------------------------------
    // Directory and path handling
    // -------------------------------------------------------------------------

    private void ensureDirectoriesExist() {
        ensureDirectory(configDirectory, "config");
        ensureDirectory(ttfDirectory, "TTF");
    }

    private boolean ensureDirectory(Path directory, String description) {
        try {
            if (Files.exists(directory) && !Files.isDirectory(directory)) {
                LOGGER.severe(() -> description + " path is not a directory: " + directory);
                return false;
            }

            Files.createDirectories(directory);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create " + description + " directory: " + directory, e);
            return false;
        }
    }

    private Optional<String> normalizeConfigName(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }

        String name = rawName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(CONFIG_EXTENSION)) {
            name = name.substring(0, name.length() - CONFIG_EXTENSION.length()).trim();
        }

        if (!isSafeSinglePathSegment(name) || name.equalsIgnoreCase(ALTS_CONFIG_NAME)) {
            return Optional.empty();
        }

        return Optional.of(name);
    }

    private static boolean isSafeSinglePathSegment(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('\0') >= 0) {
            return false;
        }

        try {
            Path path = Path.of(name);
            return path.getNameCount() == 1
                    && path.getParent() == null
                    && !name.contains(":")
                    && !name.contains("/")
                    && !name.contains("\\");
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private Path configPath(String normalizedName) {
        return configDirectory.resolve(normalizedName + CONFIG_EXTENSION).normalize();
    }

    private boolean writeStringAtomically(Path target, String content) {
        Path parent = target.getParent();
        if (parent != null && !ensureDirectory(parent, "parent")) {
            return false;
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write file: " + target, e);
            return false;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupFailure) {
                    LOGGER.log(Level.FINE, "Failed to remove temporary file: " + tempFile, cleanupFailure);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fonts
    // -------------------------------------------------------------------------

    public List<String> scanTtfFonts() {
        if (!Files.isDirectory(ttfDirectory)) {
            return List.of();
        }

        List<String> fonts = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ttfDirectory,
                path -> Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(TTF_EXTENSION))) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                fonts.add(fileName.substring(0, fileName.length() - TTF_EXTENSION.length()));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to scan TTF directory: " + ttfDirectory, e);
        }

        fonts.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(fonts);
    }

    public File getTtfFontFile(String rawName) {
        if (rawName == null || DEFAULT_FONT_NAME.equalsIgnoreCase(rawName.trim())) {
            return null;
        }

        String fontName = rawName.trim();
        if (!isSafeSinglePathSegment(fontName)) {
            LOGGER.warning(() -> "Invalid TTF font name: " + rawName);
            return null;
        }

        Path fontRoot = ttfDirectory.toAbsolutePath().normalize();
        Path fontFile = fontRoot.resolve(fontName + TTF_EXTENSION).normalize();
        if (!fontFile.startsWith(fontRoot) || !Files.isRegularFile(fontFile)) {
            return null;
        }
        return fontFile.toFile();
    }

    // -------------------------------------------------------------------------
    // Lookup indexes
    // -------------------------------------------------------------------------

    private void rebuildModuleIndex() {
        modulesByName = moduleManager.getModules().stream()
                .collect(Collectors.toUnmodifiableMap(
                        module -> normalizedLookupKey(module.getName()),
                        Function.identity(),
                        (first, duplicate) -> {
                            LOGGER.warning(() -> "Duplicate module name: " + duplicate.getName());
                            return first;
                        }));
    }

    private static Map<String, ValueParent> indexValues(Module module) {
        return module.getValues().stream()
                .collect(Collectors.toMap(
                        value -> normalizedLookupKey(value.getName()),
                        Function.identity(),
                        (first, duplicate) -> first,
                        HashMap::new));
    }

    private static String normalizedLookupKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------------
    // Events and selected config name
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    @EventTarget
    public void shutdown(ShutdownEvent event) {
        saveConfig(); // saveConfig already persists the selected config name.
        LOGGER.info(() -> "Shutdown completed, last config: " + Gemini.lastConfigName);
    }

    public void loadConfigName() {
        if (!Files.isRegularFile(configNameFile)) {
            setDefaultConfigName(true);
            return;
        }

        try {
            String storedName = Files.readString(configNameFile, StandardCharsets.UTF_8);
            Optional<String> normalizedName = normalizeConfigName(storedName);
            if (normalizedName.isPresent()) {
                Gemini.lastConfigName = normalizedName.get();
            } else {
                LOGGER.warning(() -> "Invalid stored config name, resetting: " + storedName.trim());
                setDefaultConfigName(true);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load config name: " + configNameFile, e);
            setDefaultConfigName(false);
        }
    }

    public void saveConfigName() {
        String normalizedName = normalizeConfigName(Gemini.lastConfigName)
                .orElse(DEFAULT_CONFIG_NAME);

        if (writeStringAtomically(configNameFile, normalizedName)) {
            Gemini.lastConfigName = normalizedName;
        }
    }

    private void setDefaultConfigName(boolean persist) {
        Gemini.lastConfigName = DEFAULT_CONFIG_NAME;
        if (persist) {
            saveConfigName();
        }
    }

    // -------------------------------------------------------------------------
    // Configuration save/create/delete
    // -------------------------------------------------------------------------

    public void saveConfig() {
        saveConfig(Optional.ofNullable(Gemini.lastConfigName).orElse(DEFAULT_CONFIG_NAME));
    }

    public void saveConfig(String rawName) {
        Optional<String> normalizedName = normalizeConfigName(rawName);
        if (normalizedName.isEmpty()) {
            LOGGER.warning(() -> "Invalid config name: " + rawName);
            return;
        }

        String name = normalizedName.get();
        Path file = configPath(name);
        if (writeStringAtomically(file, buildConfigJson().toString(4))) {
            Gemini.lastConfigName = name;
            saveConfigName();
            LOGGER.info(() -> "Configuration saved: " + file);
        }
    }

    public boolean createConfig(String rawName) {
        Optional<String> normalizedName = normalizeConfigName(rawName);
        if (normalizedName.isEmpty()) {
            LOGGER.warning(() -> "Invalid config name: " + rawName);
            return false;
        }

        String name = normalizedName.get();
        Path file = configPath(name);
        if (Files.exists(file)) {
            return false;
        }

        if (!writeStringAtomically(file, buildConfigJson().toString(4))) {
            return false;
        }

        Gemini.lastConfigName = name;
        saveConfigName();
        return true;
    }

    public boolean deleteConfig(String rawName) {
        Optional<String> normalizedName = normalizeConfigName(rawName);
        if (normalizedName.isEmpty()) {
            LOGGER.warning(() -> "Invalid config name: " + rawName);
            return false;
        }

        String name = normalizedName.get();
        Path file = configPath(name);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted && name.equalsIgnoreCase(Gemini.lastConfigName)) {
                setDefaultConfigName(true);
            }
            return deleted;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete configuration: " + file, e);
            return false;
        }
    }

    private JSONObject buildConfigJson() {
        JSONArray modules = new JSONArray();
        for (Module module : moduleManager.getModules()) {
            modules.put(buildModuleJson(module));
        }
        return new JSONObject().put(JSON_MODULES, modules);
    }

    private JSONObject buildModuleJson(Module module) {
        JSONArray values = new JSONArray();
        for (ValueParent value : module.getValues()) {
            JSONObject serialized = buildValueJson(value);
            if (serialized != null) {
                values.put(serialized);
            }
        }

        return new JSONObject()
                .put(JSON_NAME, module.getName())
                .put("category", module.getModuleEnum().name())
                .put("enabled", module.enabled)
                .put("favorite", module.favorite)
                .put("key", module.key)
                .put("hudX", module.hudX)
                .put("hudY", module.hudY)
                .put(JSON_VALUES, values);
    }

    /** Returns null for unsupported or failed values instead of emitting partial JSON. */
    private JSONObject buildValueJson(ValueParent value) {
        JSONObject json = new JSONObject().put(JSON_NAME, value.getName());
        try {
            switch (value) {
                case BoolValue v -> json.put(JSON_TYPE, "Bool").put(JSON_VALUE, v.enabled);
                case IntValue v -> json.put(JSON_TYPE, "Int").put(JSON_VALUE, v.getValue());
                case FloatValue v -> json.put(JSON_TYPE, "Float").put(JSON_VALUE, v.getValue());
                case ListValue v -> json.put(JSON_TYPE, "List").put(JSON_VALUE, v.get());
                case IntRangeValue v -> json.put(JSON_TYPE, "IntRange").put(JSON_VALUE,
                        new JSONObject().put("min", v.getMinValue()).put("max", v.getMaxValue()));
                case FloatRangeValue v -> json.put(JSON_TYPE, "FloatRange").put(JSON_VALUE,
                        new JSONObject().put("min", v.getMinValue()).put("max", v.getMaxValue()));
                case CheckboxValue v -> json.put(JSON_TYPE, "Checkbox").put(JSON_VALUE, serializeCheckbox(v));
                case ColorValue v -> json.put(JSON_TYPE, "Color").put(JSON_VALUE, v.getColor());
                default -> {
                    LOGGER.warning(() -> "Unsupported value type: " + value.getClass().getName());
                    return null;
                }
            }
            return json;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize value: " + value.getName(), e);
            return null;
        }
    }

    private static JSONArray serializeCheckbox(CheckboxValue checkbox) {
        JSONArray values = new JSONArray();
        for (BoolValue boolValue : checkbox.boolValues) {
            values.put(new JSONObject()
                    .put(JSON_NAME, boolValue.getName())
                    .put("enabled", boolValue.enabled));
        }
        return values;
    }

    // -------------------------------------------------------------------------
    // Configuration loading
    // -------------------------------------------------------------------------

    public void loadConfig() {
        loadConfig(Optional.ofNullable(Gemini.lastConfigName).orElse(DEFAULT_CONFIG_NAME));
    }

    public void loadConfig(String rawName) {
        Optional<String> normalizedName = normalizeConfigName(rawName);
        if (normalizedName.isEmpty()) {
            LOGGER.warning(() -> "Invalid config name: " + rawName);
            return;
        }

        String name = normalizedName.get();
        Path file = configPath(name);
        if (!Files.isRegularFile(file)) {
            LOGGER.warning(() -> "Config does not exist: " + file);
            return;
        }

        try (InputStream input = Files.newInputStream(file)) {
            JSONObject root = new JSONObject(new JSONTokener(input));
            applyConfigFromJson(root);
            Gemini.lastConfigName = name;
            saveConfigName();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read configuration: " + file, e);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Invalid configuration JSON: " + file, e);
        }
    }

    private void applyConfigFromJson(JSONObject root) {
        JSONArray modules = root.optJSONArray(JSON_MODULES);
        if (modules == null) {
            LOGGER.warning("Missing 'modules' array in configuration");
            return;
        }

        rebuildModuleIndex();
        int loadedCount = (int) IntStream.range(0, modules.length()).mapToObj(modules::optJSONObject).filter(moduleJson -> moduleJson != null && applyModuleConfig(moduleJson)).count();
        LOGGER.info(() -> "Applied configuration to " + loadedCount + " modules");
    }

    private boolean applyModuleConfig(JSONObject json) {
        String moduleName = json.optString(JSON_NAME, "").trim();
        if (moduleName.isEmpty()) {
            LOGGER.warning("Skipping module without a name");
            return false;
        }

        Module module = modulesByName.get(normalizedLookupKey(moduleName));
        if (module == null) {
            LOGGER.warning(() -> "Module not found: " + moduleName);
            return false;
        }

        if (json.has("enabled")) {
            module.setEnabled(json.optBoolean("enabled", module.enabled));
        }
        module.key = json.optInt("key", module.key);
        module.favorite = json.optBoolean("favorite", module.favorite);
        module.hudX = json.optInt("hudX", module.hudX);
        module.hudY = json.optInt("hudY", module.hudY);

        JSONArray values = json.optJSONArray(JSON_VALUES);
        if (values != null) {
            applyModuleValues(module, values);
        }
        return true;
    }

    private void applyModuleValues(Module module, JSONArray values) {
        Map<String, ValueParent> valuesByName = indexValues(module);
        int appliedCount = 0;

        for (int i = 0; i < values.length(); i++) {
            JSONObject valueJson = values.optJSONObject(i);
            if (valueJson != null && applyValueConfig(module, valuesByName, valueJson)) {
                appliedCount++;
            }
        }

        int count = appliedCount;
        LOGGER.fine(() -> "Applied " + count + " values to module: " + module.getName());
    }

    private boolean applyValueConfig(Module module,
                                     Map<String, ValueParent> valuesByName,
                                     JSONObject json) {
        String valueName = json.optString(JSON_NAME, "").trim();
        String type = json.optString(JSON_TYPE, "").trim();
        if (valueName.isEmpty() || type.isEmpty() || !json.has(JSON_VALUE)) {
            LOGGER.warning(() -> "Incomplete value entry in module: " + module.getName());
            return false;
        }

        ValueParent value = valuesByName.get(normalizedLookupKey(valueName));
        if (value == null) {
            LOGGER.warning(() -> "Value not found in " + module.getName() + ": " + valueName);
            return false;
        }
        if (!isTypeMatch(value, type)) {
            LOGGER.warning(() -> "Type mismatch for " + module.getName() + '.' + valueName
                    + ": expected " + value.getClass().getSimpleName() + ", got " + type);
            return false;
        }

        try {
            applyValue(value, type, json.get(JSON_VALUE));
            return true;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING,
                    "Failed to apply value " + valueName + " in module " + module.getName(), e);
            return false;
        }
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

    private void applyValue(ValueParent value, String type, Object jsonValue) {
        switch (type) {
            case "Bool" -> ((BoolValue) value).enabled = requireBoolean(jsonValue);
            case "Int" -> ((IntValue) value).setValue(requireNumber(jsonValue).intValue());
            case "Float" -> ((FloatValue) value).setValue(requireNumber(jsonValue).floatValue());
            case "List" -> ((ListValue) value).setMode(String.valueOf(jsonValue));
            case "IntRange" -> applyIntRangeValue((IntRangeValue) value, requireObject(jsonValue));
            case "FloatRange" -> applyFloatRangeValue((FloatRangeValue) value, requireObject(jsonValue));
            case "Checkbox" -> applyCheckboxValue((CheckboxValue) value, requireArray(jsonValue));
            case "Color" -> ((ColorValue) value).setColor(requireNumber(jsonValue).intValue());
            default -> throw new IllegalArgumentException("Unknown value type: " + type);
        }
    }

    private static Number requireNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalArgumentException("Expected number, got: " + value);
    }

    private static boolean requireBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Expected boolean, got: " + value);
    }

    private static JSONObject requireObject(Object value) {
        if (value instanceof JSONObject object) {
            return object;
        }
        throw new IllegalArgumentException("Expected JSON object, got: " + value);
    }

    private static JSONArray requireArray(Object value) {
        if (value instanceof JSONArray array) {
            return array;
        }
        throw new IllegalArgumentException("Expected JSON array, got: " + value);
    }

    private static void applyIntRangeValue(IntRangeValue value, JSONObject range) {
        int min = range.getInt("min");
        int max = range.getInt("max");
        value.setMaxValue(max);
        value.setMinValue(min);
    }

    private static void applyFloatRangeValue(FloatRangeValue value, JSONObject range) {
        float min = requireNumber(range.get("min")).floatValue();
        float max = requireNumber(range.get("max")).floatValue();
        value.setMaxValue(max);
        value.setMinValue(min);
    }

    private static void applyCheckboxValue(CheckboxValue checkbox, JSONArray array) {
        Map<String, BoolValue> valuesByName = Arrays.stream(checkbox.boolValues)
                .collect(Collectors.toMap(
                        value -> normalizedLookupKey(value.getName()),
                        Function.identity(),
                        (first, duplicate) -> first));

        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }

            String name = json.optString(JSON_NAME, "").trim();
            BoolValue value = valuesByName.get(normalizedLookupKey(name));
            if (value != null && json.has("enabled")) {
                value.enabled = json.optBoolean("enabled", value.enabled);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Alternate accounts
    // -------------------------------------------------------------------------

    public JSONArray loadAlts() {
        if (!Files.isRegularFile(altsFile)) {
            return new JSONArray();
        }

        try (InputStream input = Files.newInputStream(altsFile)) {
            return new JSONArray(new JSONTokener(input));
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to load alts: " + altsFile, e);
            return new JSONArray();
        }
    }

    public void saveAlts(JSONArray accounts) {
        if (accounts == null) {
            LOGGER.warning("Cannot save a null alts list");
            return;
        }
        writeStringAtomically(altsFile, accounts.toString(4));
    }
}
