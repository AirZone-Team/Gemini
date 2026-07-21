package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.effectDisplay.MaterialEffectRenderer;
import geminiclient.gemini.modules.impl.visual.effectDisplay.RenderEffect;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * EffectDisplay — 药水效果 HUD 模块。
 *
 * 两种可切换样式（"Mode" 选项）：
 *  - "Classic"：深色亚克力玻璃 + 左侧霓虹 accent 条（原有样式，
 *    由 {@link RenderEffect} 渲染）
 *  - "Material"：Material Design 3 浅色卡片 —— 近白 surface 圆角卡片、
 *    药水色 tonal 图标圆盘、Google Sans 双行文本（深色标题 + 药水色时长）、
 *    MD3 线性进度条与 GLSL elevation 投影（由 {@link MaterialEffectRenderer} 渲染）。
 *    卡片配色 / 圆角 / 阴影 / 进度条 / 动态取色均可配置。
 */
public class EffectDisplay extends Module {

    // ==================== CONFIGURATION VALUES ====================

    public final ListValue mode = new ListValue("Mode",
            "Classic", new String[]{"Classic", "Material"});

    // ---- Material (MD3) 卡片样式（仅 Material 模式下显示） ----
    public final ColorValue surfaceColor = new ColorValue("Surface Color", 0xFFF5F4F8, () -> mode.is("Material"));
    public final ColorValue titleColor   = new ColorValue("Title Color",   0xFF26242E, () -> mode.is("Material"));
    public final IntValue   cardRadius   = new IntValue("Radius", 12, 0, 20, () -> mode.is("Material"));
    public final BoolValue  cardShadow   = new BoolValue("Shadow", true, () -> mode.is("Material"));
    public final BoolValue  progressBar  = new BoolValue("Progress Bar", true, () -> mode.is("Material"));
    public final BoolValue  dynamicColor = new BoolValue("Dynamic Color", true, () -> mode.is("Material"));

    public EffectDisplay() {
        super("EffectDisplay", ModuleEnum.Visual);
        hudX = 6;
        hudY = 200;
        addValue(mode,
                surfaceColor, titleColor,
                cardRadius, cardShadow, progressBar, dynamicColor);
    }

    /** 当前是否使用 Material (MD3) 浅色卡片样式。 */
    public boolean isMaterial() {
        return mode.is("Material");
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (isMaterial()) {
            MaterialEffectRenderer.getInstance().render(event.guiGraphics(), this);
        } else {
            RenderEffect.getInstance().render(event.guiGraphics(), this);
        }
    }

    @Override
    public void renderEditorOutline(GuiGraphicsExtractor g) {
        if (isMaterial()) {
            MaterialEffectRenderer.getInstance().renderOutline(g, this);
        } else {
            RenderEffect.getInstance().renderOutline(g, this);
        }
    }
}
