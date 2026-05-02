package geminiclient.gemini.base;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.ModuleManager;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.annotation.Native;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JavaToCSharpIPC {
    private static Process csharpProcess;
    private static BufferedWriter csharpIn;
    private static BufferedReader csharpOut;

    public static void toCS(ModuleManager moduleManager) {
        try {
            // 查找内嵌的 WpfApp1.exe 资源
            URL url = JavaToCSharpIPC.class.getClassLoader().getResource("publish/WpfApp1.exe");
            if (url == null) {
                System.err.println("WpfApp1.exe not found in resources!");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(url.getFile());
            pb.redirectErrorStream(true); // 合并标准错误到标准输出，便于排查
            csharpProcess = pb.start();

            csharpIn = new BufferedWriter(
                    new OutputStreamWriter(csharpProcess.getOutputStream(), StandardCharsets.UTF_8));
            csharpOut = new BufferedReader(
                    new InputStreamReader(csharpProcess.getInputStream(), StandardCharsets.UTF_8));

            // 发送全量快照
            JSONObject snapshot = buildSnapshot(moduleManager);
            System.out.println("Sending snapshot: " + snapshot.toString());
            sendMessage(snapshot);
            // 启动监听线程，接收来自 C# 的 setting_update
            new Thread(() -> {
                String line;
                try {
                    while ((line = csharpOut.readLine()) != null) {
                        handleMessage(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject buildSnapshot(ModuleManager moduleManager) {
        // 按 Category 分组
        Map<ModuleEnum, List<Module>> grouped = new HashMap<>();
        for (Module module : moduleManager.getModules()) {
            grouped.computeIfAbsent(module.getModuleEnum(), k -> new ArrayList<>()).add(module);
        }

        JSONArray categories = new JSONArray();
        for (ModuleEnum categoryEnum : ModuleEnum.values()) {
            List<Module> mods = grouped.getOrDefault(categoryEnum, Collections.emptyList());
            JSONObject catJson = new JSONObject();
            catJson.put("name", categoryEnum.name());

            JSONArray modules = new JSONArray();
            for (Module mod : mods) {
                modules.put(buildModuleJson(mod));
            }
            catJson.put("modules", modules);
            categories.put(catJson);
        }

        JSONObject root = new JSONObject();
        root.put("type", "settings_snapshot");
        root.put("categories", categories);
        return root;
    }

    private static JSONObject buildModuleJson(Module module) {
        JSONObject modJson = new JSONObject();
        modJson.put("name", module.getName());
        modJson.put("enabled", module.enabled);

        JSONArray settings = new JSONArray();
        for (ValueParent value : module.getValues()) {
            JSONObject settingJson = buildSettingJson(value);
            if (settingJson != null) {
                settings.put(settingJson);
            }
        }
        modJson.put("settings", settings);
        return modJson;
    }

    private static JSONObject buildSettingJson(ValueParent value) {
        JSONObject obj = new JSONObject();
        obj.put("name", value.getName());

        if (value instanceof BoolValue boolVal) {
            obj.put("type", "bool");
            obj.put("value", boolVal.enabled);
        } else if (value instanceof IntValue intVal) {
            obj.put("type", "int_slider");
            obj.put("value", intVal.getValue());
            obj.put("min", intVal.getMin());  // 确保 IntValue 有这些方法
            obj.put("max", intVal.getMax());
        } else if (value instanceof FloatValue floatVal) {
            obj.put("type", "slider");
            obj.put("value", floatVal.getValue());
            obj.put("min", floatVal.getMin()); // 同理
            obj.put("max", floatVal.getMax());
        } else if (value instanceof ListValue listVal) {
            obj.put("type", "mode");
            JSONArray modes = new JSONArray(listVal.getList()); // 假设 getModes() 返回 List<String>
            obj.put("modes", modes);
            obj.put("selectedIndex", listVal.index);
        } else if (value instanceof IntRangeValue intRange) {
            obj.put("type", "int_range");
            obj.put("minValue", intRange.getMinValue());
            obj.put("maxValue", intRange.getMaxValue());
            obj.put("min", intRange.getMin());
            obj.put("max", intRange.getMax());
        } else if (value instanceof FloatRangeValue floatRange) {
            obj.put("type", "float_range");
            obj.put("minValue", floatRange.getMinValue());
            obj.put("maxValue", floatRange.getMaxValue());
            obj.put("min", floatRange.getMin());
            obj.put("max", floatRange.getMax());
        } else if (value instanceof CheckboxValue checkbox) {
            obj.put("type", "checkbox");
            JSONArray items = new JSONArray();
            for (BoolValue bv : checkbox.boolValues) {
                JSONObject item = new JSONObject();
                item.put("name", bv.getName());
                item.put("value", bv.enabled);
                items.put(item);
            }
            obj.put("items", items);
        } else {
            return null; // 未知类型跳过
        }
        return obj;
    }

    private static void sendMessage(JSONObject msg) {
        try {
            csharpIn.write(msg.toString() + "\n");
            csharpIn.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 处理 C# 回传的 setting_update
    private static void handleMessage(String json) {
        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.optString("type");
            if ("setting_update".equals(type)) {
                String moduleName = msg.getString("module");
                String settingName = msg.getString("setting");
                Object value = msg.get("value");

                // 根据模块名和设置名找到对应的 ValueParent 并更新
                applySettingUpdate(moduleName, settingName, value, msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            JSONObject msg = new JSONObject(json);
            String type = msg.optString("type");
            if ("setting_update".equals(type)) {
                // ... 原有逻辑 ...
            } else if ("module_update".equals(type)) {
                String moduleName = msg.getString("module");
                boolean enabled = msg.getBoolean("enabled");
                applyModuleUpdate(moduleName, enabled);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void applyModuleUpdate(String moduleName, boolean enabled) {
        ModuleManager mm = Gemini.moduleManager;
        mm.getModules().stream()
                .filter(m -> m.getName().equalsIgnoreCase(moduleName))
                .findFirst()
                .ifPresent(module -> module.setEnabled(enabled));
        Gemini.fileSystem.saveConfig();
        // 如果 Module 类有 setEnabled 方法触发监听器，建议调用 setEnabled(enabled)
    }

    private static void applySettingUpdate(String moduleName, String settingName,
                                           Object value, JSONObject fullMsg) {
        ModuleManager mm = Gemini.moduleManager; // 假设全局可访问
        Optional<Module> moduleOpt = mm.getModules().stream()
                .filter(m -> m.getName().equalsIgnoreCase(moduleName))
                .findFirst();
        if (moduleOpt.isEmpty()) return;

        Module module = moduleOpt.get();
        Optional<ValueParent> valueOpt = module.getValues().stream()
                .filter(v -> v.getName().equalsIgnoreCase(settingName))
                .findFirst();
        if (valueOpt.isEmpty()) return;

        ValueParent vp = valueOpt.get();
        // 根据具体类型更新值
        if (vp instanceof BoolValue bv) {
            bv.enabled = (boolean) value;
        } else if (vp instanceof IntValue iv) {
            iv.setValue((int) value);
        } else if (vp instanceof FloatValue fv) {
            fv.setValue(((Number) value).floatValue());
        } else if (vp instanceof ListValue lv) {
            int idx = ((Number) value).intValue();
            lv.setMode(lv.getList().get(idx)); // 需要根据实际 ListValue API 调整
        } else if (vp instanceof IntRangeValue irv) {
            if (fullMsg.has("minValue")) irv.setMinValue(fullMsg.getInt("minValue"));
            if (fullMsg.has("maxValue")) irv.setMaxValue(fullMsg.getInt("maxValue"));
        } else if (vp instanceof FloatRangeValue frv) {
            if (fullMsg.has("minValue")) frv.setMinValue((float) fullMsg.getDouble("minValue"));
            if (fullMsg.has("maxValue")) frv.setMaxValue((float) fullMsg.getDouble("maxValue"));
        } else if (vp instanceof CheckboxValue chkv) {
            JSONObject items = (JSONObject) value; // 实际是 JSONObject
            for (String key : items.keySet()) {
                Arrays.stream(chkv.boolValues)
                        .filter(bv -> bv.getName().equalsIgnoreCase(key))
                        .findFirst()
                        .ifPresent(bv -> bv.enabled = items.getBoolean(key));
            }
        }
        Gemini.fileSystem.saveConfig();
        // 必要时通知模块/UI 刷新（如果有观察者模式）
    }

    // 程序退出时关闭进程
    public static void shutdown() {
        if (csharpProcess != null && csharpProcess.isAlive()) {
            csharpProcess.destroy();
        }
    }
}