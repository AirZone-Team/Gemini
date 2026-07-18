package geminiclient.gemini.modules;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.JavaToCSharpIPC;
import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.modules.impl.visual.notice.ModuleNotification;
import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Module implements MinecraftInstance {
    protected String name;
    private final ModuleEnum moduleEnum;
    public boolean enabled = false;
    public boolean favorite = false;
    public int key = 0;
    private final List<ValueParent> values = new ArrayList<>();

    public float animationXOffset = 0.0f;

    public int hudX = 6;
    public int hudY = 50;

    public Module(String name, ModuleEnum moduleEnum) {
        this.name = name;
        this.moduleEnum = moduleEnum;
    }

    public Module(String name, ModuleEnum moduleEnum, boolean enabled) {
        this.name = name;
        this.moduleEnum = moduleEnum;
        setEnabled(enabled);
    }

    public ModuleEnum getModuleEnum() {
        return this.moduleEnum;
    }

    public List<ValueParent> getValues() {
        return this.values;
    }

    public String getName() {
        return this.name;
    }

    public void onEnabled() {
    }

    public void onDisabled() {
    }

    public void addValue(ValueParent... valueParents) {
        for (ValueParent vp : valueParents) {
            // 注册值变更回调：本地值变化时自动向 C# 发送增量
            String moduleName = this.name;
            vp.setOnChange(() -> JavaToCSharpIPC.sendSettingUpdate(moduleName, vp.getName(), vp));
            values.add(vp);

            // CheckboxValue 的子 BoolValue 需要透传变更到父级，使父级 Checkbox 的 onChange 被触发
            if (vp instanceof CheckboxValue chkv) {
                for (BoolValue bv : chkv.boolValues) {
                    bv.setOnChange(vp::notifyChange);
                }
            }
        }
    }

    public void setEnabled(boolean b) {
        if (this.enabled == b)
            return;

        this.enabled = b;

        // 向 C# 发送模块开关增量
        JavaToCSharpIPC.sendModuleUpdate(name, this.enabled);

        if (b) {
            animationXOffset = 100f;
            Gemini.eventManager.register(this);
            onEnabled();
            Gemini.notificationManager.addNotification(ModuleNotification.NotificationLevel.INFO,
                    "Module", "Enabled: " + this.name, 2000);
        } else {
            Gemini.eventManager.unregister(this);
            onDisabled();
            Gemini.notificationManager.addNotification(ModuleNotification.NotificationLevel.ERROR,
                    "Module", "Disabled: " + this.name, 2000);
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Override in HUD modules to render a placeholder outline + dummy text
     * when the chat screen is open (drag-editor mode), even if the module is disabled.
     */
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {}
}
