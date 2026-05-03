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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class JavaToCSharpIPC {
    private static Process csharpProcess;
    private static BufferedWriter csharpIn;
    private static BufferedReader csharpOut;
    private static JSONArray accountsList = new JSONArray();
    static URL url = JavaToCSharpIPC.class.getClassLoader().getResource("publish/WpfApp1.exe");
    static ProcessBuilder pb = new ProcessBuilder(url != null ? url.getFile() : null);
    
    public static void startExe() {
        try {
            if (url == null) {
                System.err.println("WpfApp1.exe not found in resources!");
                return;
            }
            pb.redirectErrorStream(true); // 合并标准错误到标准输出，便于排查
            csharpProcess = pb.start();
            csharpIn = new BufferedWriter(
                    new OutputStreamWriter(csharpProcess.getOutputStream(), StandardCharsets.UTF_8));
            csharpOut = new BufferedReader(
                    new InputStreamReader(csharpProcess.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startReceive() {
        new Thread(() -> {
            String line;
            try {
                while ((line = csharpOut.readLine()) != null) {
                    System.out.println(csharpOut);
                    handleMessage(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void toCSAccount() {
        accountsList = Gemini.fileSystem.loadAlts();
        syncAltsToCS();
    }

    public static void toCS(ModuleManager moduleManager) {
        try {
            if (csharpIn == null || csharpOut == null)
                return;

            JSONObject snapshot = buildSnapshot(moduleManager);
            System.out.println("Sending snapshot: " + snapshot);
            sendMessage(snapshot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void syncAltsToCS() {
        try {
            JSONObject root = new JSONObject();
            root.put("type", "sync_alts");
            root.put("accounts", accountsList);
            sendMessage(root);
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

        switch (value) {
            case BoolValue boolVal -> {
                obj.put("type", "bool");
                obj.put("value", boolVal.enabled);
            }
            case IntValue intVal -> {
                obj.put("type", "int_slider");
                obj.put("value", intVal.getValue());
                obj.put("min", intVal.getMin());  // 确保 IntValue 有这些方法

                obj.put("max", intVal.getMax());
            }
            case FloatValue floatVal -> {
                obj.put("type", "slider");
                obj.put("value", floatVal.getValue());
                obj.put("min", floatVal.getMin()); // 同理

                obj.put("max", floatVal.getMax());
            }
            case ListValue listVal -> {
                obj.put("type", "mode");
                JSONArray modes = new JSONArray(listVal.getList()); // 假设 getModes() 返回 List<String>

                obj.put("modes", modes);
                obj.put("selectedIndex", listVal.index);
            }
            case IntRangeValue intRange -> {
                obj.put("type", "int_range");
                obj.put("minValue", intRange.getMinValue());
                obj.put("maxValue", intRange.getMaxValue());
                obj.put("min", intRange.getMin());
                obj.put("max", intRange.getMax());
            }
            case FloatRangeValue floatRange -> {
                obj.put("type", "float_range");
                obj.put("minValue", floatRange.getMinValue());
                obj.put("maxValue", floatRange.getMaxValue());
                obj.put("min", floatRange.getMin());
                obj.put("max", floatRange.getMax());
            }
            case CheckboxValue checkbox -> {
                obj.put("type", "checkbox");
                JSONArray items = new JSONArray();
                for (BoolValue bv : checkbox.boolValues) {
                    JSONObject item = new JSONObject();
                    item.put("name", bv.getName());
                    item.put("value", bv.enabled);
                    items.put(item);
                }
                obj.put("items", items);
            }
            default -> {
                return null; // 未知类型跳过
            }
        }
        return obj;
    }

    private static void sendMessage(JSONObject msg) {
        try {
            System.out.println("msg: " + msg.toString());
            csharpIn.write(msg + "\n");
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
                applySettingUpdate(moduleName, settingName, value, msg);
            } else if ("module_update".equals(type)) {
                String moduleName = msg.getString("module");
                boolean enabled = msg.getBoolean("enabled");
                applyModuleUpdate(moduleName, enabled);
            } else if ("account_update".equals(type)) {
                handleAccountUpdate(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleAccountUpdate(JSONObject msg) {
        String action = msg.getString("action");
        String accountName = msg.getString("accountName");
        String loginType = msg.optString("loginType", "Offline");
        String uuid = msg.optString("uuid", "");
        String accessToken = msg.optString("accessToken", "");

        System.out.println("[IPC] Account " + action + ": " + accountName + " | Type: " + loginType);

        switch (action) {
            case "add" -> {
                // 构建新的账号 JSON 并添加进列表
                JSONObject account = new JSONObject();
                account.put("accountName", accountName);
                account.put("loginType", loginType);
                account.put("uuid", uuid);
                account.put("accessToken", accessToken);
                accountsList.put(account);

                // ★ 保存到本地 configs/alts.json
                Gemini.fileSystem.saveAlts(accountsList);
            }
            case "delete" -> {
                // 遍历查找并删除该账号
                for (int i = 0; i < accountsList.length(); i++) {
                    if (accountsList.getJSONObject(i).getString("accountName").equals(accountName)) {
                        accountsList.remove(i);
                        break;
                    }
                }
                // ★ 保存到本地 configs/alts.json
                Gemini.fileSystem.saveAlts(accountsList);
            }
            case "apply" -> {
                AltHelper.account(mc, action, accountName, loginType, parseUUIDFromString(uuid), accessToken);
            }
        }
    }

    private static UUID parseUUIDFromString(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) {
            return new UUID(0L, 0L);
        }

        // 移除可能存在的横杠
        String cleanUuid = uuidStr.replace("-", "");

        if (cleanUuid.length() == 32) {
            // 前 16 个字符 (64 bits) 作为 mostSigBits
            long mostSigBits = Long.parseUnsignedLong(cleanUuid.substring(0, 16), 16);
            // 后 16 个字符 (64 bits) 作为 leastSigBits
            long leastSigBits = Long.parseUnsignedLong(cleanUuid.substring(16, 32), 16);

            // 使用你要求的构造方式
            return new UUID(mostSigBits, leastSigBits);
        } else {
            // Fallback: 如果传入的是标准格式，退回使用默认解析
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return new UUID(0L, 0L);
            }
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
        switch (vp) {
            case BoolValue bv -> bv.enabled = (boolean) value;
            case IntValue iv -> iv.setValue((int) value);
            case FloatValue fv -> fv.setValue(((Number) value).floatValue());
            case ListValue lv -> {
                int idx = ((Number) value).intValue();
                lv.setMode(lv.getList().get(idx)); // 需要根据实际 ListValue API 调整
            }
            case IntRangeValue irv -> {
                if (fullMsg.has("minValue")) irv.setMinValue(fullMsg.getInt("minValue"));
                if (fullMsg.has("maxValue")) irv.setMaxValue(fullMsg.getInt("maxValue"));
            }
            case FloatRangeValue frv -> {
                if (fullMsg.has("minValue")) frv.setMinValue((float) fullMsg.getDouble("minValue"));
                if (fullMsg.has("maxValue")) frv.setMaxValue((float) fullMsg.getDouble("maxValue"));
            }
            case CheckboxValue chkv -> {
                JSONObject items = (JSONObject) value; // 实际是 JSONObject

                for (String key : items.keySet()) {
                    Arrays.stream(chkv.boolValues)
                            .filter(bv -> bv.getName().equalsIgnoreCase(key))
                            .findFirst()
                            .ifPresent(bv -> bv.enabled = items.getBoolean(key));
                }
            }
            default -> {
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