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
 * 配置管理器，负责使用 org.json 库保存和加载模块配置。
 */
public class FileSystem {

    private final ModuleManager moduleManager;
    // 配置文件路径，实际应用中应确保文件可读写
    private static final String CONFIG_FILE_PATH = Minecraft.getInstance().gameDirectory.getAbsolutePath() + "gemini\\configs\\config.json";

    public FileSystem(ModuleManager moduleManager) {
        Gemini.eventManager.register(this);
        this.moduleManager = moduleManager;
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * 根据名称在 ModuleManager 中查找模块。
     */
    private Optional<Module> getModuleByName(String name) {
        return moduleManager.getModules().stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * 根据名称在指定模块中查找值。
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
    }

    // ---

    // =========================================================================
    // 配置保存方法 (Save Config)
    // =========================================================================

    /**
     * 遍历所有模块和它们的值，将配置转换为 JSONObject，并模拟保存到文件。
     */
    public void saveConfig() {
        JSONObject configRoot = new JSONObject();
        JSONArray modulesArray = new JSONArray();
        File configFile = new File(CONFIG_FILE_PATH);

        List<Module> modules = moduleManager.getModules();

        for (Module module : modules) {
            JSONObject moduleObject = new JSONObject();

            // 模块基本信息
            moduleObject.put("name", module.getName());
            moduleObject.put("category", module.getModuleEnum().name());
            moduleObject.put("enabled", module.enabled); // 模块是否启用
            moduleObject.put("key", module.key); // 模块的绑定键

            // 模块的值设置 (Values)
            JSONArray valuesArray = new JSONArray();

            for (ValueParent value : module.getValues()) {
                JSONObject valueObject = new JSONObject();
                String valueName = value.getName();

                valueObject.put("name", valueName);

                // 检查值的具体类型并提取数据
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
                        valueObject.put("value", listValue.get()); // 当前选中的模式
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

        // --- 模拟保存到文件 ---
        System.out.println("--- 尝试保存配置到 " + CONFIG_FILE_PATH + " ---");
        System.out.println(configRoot.toString(4));
        System.out.println("--- 配置保存完成 ---");
        // --- 真实文件写入代码（需处理 IO 异常）如下： ---
        try {
            // 1. 检查并创建父目录 (如果路径是 "configs/config.json")
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                // 如果父目录不存在，尝试创建它
                if (parentDir.mkdirs()) {
                    System.out.println("已创建配置目录: " + parentDir.getAbsolutePath());
                } else {
                    System.err.println("错误：无法创建配置目录。");
                    return;
                }
            }

            // 2. 检查并创建文件 (如果文件不存在)
            if (!configFile.exists()) {
                if (configFile.createNewFile()) {
                    System.out.println("已创建新的配置文件: " + CONFIG_FILE_PATH);
                } else {
                    System.err.println("错误：无法创建配置文件。");
                    return;
                }
            }

            // 3. 写入配置内容
            try (FileWriter fileWriter = new FileWriter(configFile)) {
                fileWriter.write(configRoot.toString(4));
                fileWriter.flush();
                System.out.println("配置已成功写入文件: " + CONFIG_FILE_PATH);
            }

        } catch (IOException e) {
            System.err.println("错误：保存配置失败 (IO异常)。请检查文件权限和路径。");
            e.printStackTrace();
        }
    }

    // ---

    // =========================================================================
    // 配置加载方法 (Load Config)
    // =========================================================================

    /**
     * 模拟从文件读取 JSON 配置并应用到模块和值。
     */
    public void loadConfig() {
        System.out.println("--- 开始加载配置 (从文件读取) ---");

        File configFile = new File(CONFIG_FILE_PATH);

        if (!configFile.exists()) {
            System.out.printf("警告: 配置文件 '%s' 不存在，跳过加载。\n", CONFIG_FILE_PATH);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            // 1. 使用 JSONTokener 从 BufferedReader 流中解析 JSON
            JSONObject configRoot = new JSONObject(new JSONTokener(reader));

            if (!configRoot.has("modules")) {
                System.err.println("错误：JSON 根对象中缺少 'modules' 数组。");
                return;
            }

            JSONArray modulesArray = configRoot.getJSONArray("modules");

            // 2. 遍历 JSON 中的模块配置
            for (int i = 0; i < modulesArray.length(); i++) {
                JSONObject moduleJson = modulesArray.getJSONObject(i);
                String moduleName = moduleJson.getString("name");

                Optional<Module> moduleOpt = getModuleByName(moduleName);
                if (moduleOpt.isEmpty()) {
                    System.err.printf("警告: 未找到名为 '%s' 的模块，跳过加载。\n", moduleName);
                    continue;
                }
                Module module = moduleOpt.get();

                // 3. 设置模块状态
                if (moduleJson.has("enabled")) {
                    module.enabled = moduleJson.getBoolean("enabled");
                }
                if (moduleJson.has("key")) {
                    module.key = moduleJson.getInt("key");
                }

                System.out.printf("  [模块] %s -> 已设置基本状态\n", moduleName);

                // 4. 遍历模块中的值配置
                if (moduleJson.has("values")) {
                    JSONArray valuesArray = moduleJson.getJSONArray("values");
                    for (int j = 0; j < valuesArray.length(); j++) {
                        JSONObject valueJson = valuesArray.getJSONObject(j);
                        String valueName = valueJson.getString("name");
                        String valueType = valueJson.getString("type");

                        Optional<ValueParent> valueOpt = getValueByName(module, valueName);
                        if (valueOpt.isEmpty()) {
                            System.err.printf("  警告: 模块 %s 中未找到值 '%s'，跳过加载。\n", moduleName, valueName);
                            continue;
                        }

                        // 5. 应用值
                        if (valueJson.has("value")) {
                            applyValue(valueOpt.get(), valueType, valueJson.get("value"));
                            System.out.printf("    [值] %s (%s) -> 已设置\n", valueName, valueType);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("错误：读取配置文件失败。");
            e.printStackTrace();
        } catch (Exception e) {
            // 捕获 JSON 解析错误或结构错误
            System.err.println("错误：配置加载过程中发生 JSON 格式/结构异常：");
            e.printStackTrace();
        }

        System.out.println("--- 配置加载完成 ---");
    }

    /**
     * 根据类型将 JSON 值应用到 ValueParent 实例上。
     * @param value 目标 ValueParent 实例
     * @param type JSON中记录的类型字符串
     * @param jsonValue JSON中的值对象 (可以是 boolean, int, float, String, JSONObject)
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
                    // JSON 将所有数字解析为 Number (通常是 Double 或 Integer)
                    ((FloatValue) value).setValue(((Number) jsonValue).floatValue());
                    break;
                case "List":
                    ((ListValue) value).setMode((String) jsonValue);
                    break;
                case "IntRange":
                    JSONObject intRange = (JSONObject) jsonValue;
                    IntRangeValue irValue = (IntRangeValue) value;
                    // 先设置 MaxValue 再设置 MinValue，以避免范围约束错误
                    irValue.setMaxValue(intRange.getInt("max"));
                    irValue.setMinValue(intRange.getInt("min"));
                    break;
                case "FloatRange":
                    JSONObject floatRange = (JSONObject) jsonValue;
                    FloatRangeValue frValue = (FloatRangeValue) value;
                    // 先设置 MaxValue 再设置 MinValue
                    frValue.setMaxValue((float) floatRange.getDouble("max"));
                    frValue.setMinValue((float) floatRange.getDouble("min"));
                    break;
                default:
                    System.err.println("  警告: 未知值类型: " + type);
                    break;
            }
        } catch (ClassCastException | NullPointerException e) {
            System.err.printf("  警告: 设置值 '%s' 时发生类型转换错误。配置与代码不匹配。\n", value.getName());
        }
    }
}