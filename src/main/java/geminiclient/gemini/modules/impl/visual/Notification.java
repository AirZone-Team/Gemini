package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Notification — HUD 通知渲染模块。
 *
 * 两种可切换样式（"Mode" 选项）：
 *  - "Classic"：深色磨砂共享容器，圆形图标 + 单行文本（原有样式）
 *  - "Mellow"：浅色粉彩独立卡片 —— 近白圆角卡片、淡紫图标圆盘、
 *    双行 Google Sans 文本（深色标题 + 紫色消息）、右侧 Material 3
 *    圆形波浪进度条（浅色轨道 + 垂直渐变波浪进度带，显示通知生命周期进度）
 *    与 GLSL 柔和投影。卡片配色 / 圆角 / 阴影 / 进度条均可配置。
 */
public class Notification extends Module {

    // ==================== CONFIGURATION VALUES ====================

    public final ListValue mode = new ListValue("Mode",
            "Classic", new String[]{"Classic", "Mellow"});

    // ---- Mellow 卡片样式（仅 Mellow 模式下显示） ----
    public final ColorValue cardColor          = new ColorValue("Card Color",        0xFFF5F4F8, () -> mode.is("Mellow"));
    public final ColorValue titleColor         = new ColorValue("Title Color",       0xFF26242E, () -> mode.is("Mellow"));
    public final ColorValue messageColor       = new ColorValue("Message Color",     0xFF7E74D8, () -> mode.is("Mellow"));
    public final ColorValue iconBgColor        = new ColorValue("Icon BG",           0xFFE6E2F7, () -> mode.is("Mellow"));
    public final ColorValue iconColor          = new ColorValue("Icon Color",        0xFF2B2937, () -> mode.is("Mellow"));
    public final ColorValue ringTrackColor     = new ColorValue("Ring Track",        0xFFE1DCF7, () -> mode.is("Mellow"));
    public final ColorValue ringWaveTopColor    = new ColorValue("Ring Wave Top",    0xFF7E74D8, () -> mode.is("Mellow"));
    public final ColorValue ringWaveBottomColor = new ColorValue("Ring Wave Bottom", 0xFF564A9E, () -> mode.is("Mellow"));
    public final IntValue   cardRadius     = new IntValue("Radius", 9, 0, 16, () -> mode.is("Mellow"));
    public final BoolValue  cardShadow     = new BoolValue("Shadow", true, () -> mode.is("Mellow"));
    public final BoolValue  progressRing   = new BoolValue("Progress Ring", true, () -> mode.is("Mellow"));

    public Notification() {
        super("Notification", ModuleEnum.Visual);
        hudX = 620;
        hudY = 350;
        addValue(mode,
                cardColor, titleColor, messageColor,
                iconBgColor, iconColor, ringTrackColor,
                ringWaveTopColor, ringWaveBottomColor,
                cardRadius, cardShadow, progressRing);
    }

    /** 当前是否使用 Mellow 浅色卡片样式。 */
    public boolean isMellow() {
        return mode.is("Mellow");
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender2D(Render2DEvent event) {
            Gemini.notificationManager.renderAll(event.guiGraphics(), this);
    }

    @Override
    public void renderEditorOutline(GuiGraphicsExtractor g) {
        Gemini.notificationManager.renderOutline(g, this);
    }
}
