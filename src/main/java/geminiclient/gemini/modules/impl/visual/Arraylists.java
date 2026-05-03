package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;

public class Arraylists extends Module {
    // 配置选项
    public final BoolValue mainBackground = new BoolValue("Main Bkgrd", true);
    public final BoolValue moduleBackground = new BoolValue("Module Bkgrd", true);

    // ==================== 美化后的样式常量 ====================
    // 背景与阴影：更深邃的暗黑风格，增强对比度
    private static final int BG_COLOR_BASE = 0x0F0F0F;          // 极深灰（接近纯黑）
    private static final int SHADOW_COLOR_BASE = 0x000000;      // 阴影黑色
    private static final int BORDER_COLOR_BASE = 0xFFFFFF;      // 边框白色
    private static final int TEXT_COLOR_BASE = 0xF5F5F5;        // 文字采用柔和白，避免刺眼

    // 状态条颜色：更现代的马卡龙/柔和色系
    private static final int STATUS_ENABLED_COLOR = 0x43E096;   // 现代翠绿（启用）
    private static final int STATUS_DISABLED_COLOR = 0xFF5B5B;  // 柔和珊瑚红（禁用过渡）
    private static final int MODULE_BG_COLOR = 0x25000000;      // 模块背景色（更轻薄的透明黑）

    // 尺寸与排版：增加呼吸感
    private static final int STATUS_BAR_WIDTH = 2;              // 状态条稍微变细，更显精致
    private static final int PADDING_TEXT_LEFT = 6;             // 增加文本左侧留白
    private static final int PADDING_TEXT_RIGHT = 6;            // 增加文本右侧留白
    private static final int PADDING_MAIN_LEFT = 3;             // 主背景左边缘留白
    private static final int PADDING_MAIN_RIGHT = 3;            // 主背景右边缘留白
    private static final int TOP_PADDING = 4;                   // 主背景顶部内边距
    private static final int BOTTOM_PADDING = 4;                // 主背景底部内边距
    private static final int LINE_HEIGHT_PADDING = 5;           // 增加行间距，避免拥挤
    private static final int SHADOW_OFFSET = 3;                 // 阴影偏移量增加，立体感更强

    // 动画平滑度（使用更柔和的阻尼系数）
    private static final float ANIMATION_SMOOTHNESS = 0.15f;    // 替代原有的 ANIMATION_SPEED

    // 模块动画状态
    private static class ModuleAnimation {
        float xOffset = 100.0f;
        float targetY = 0;
        float currentY = 0;
    }

    private final Map<Module, ModuleAnimation> animations = new HashMap<>();
    private float animatedHeight = 0;

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
        addValue(mainBackground);
        addValue(moduleBackground);
    }

    @EventTarget
    public void render2d(Render2DEvent event) {
        List<Module> modules = Gemini.moduleManager.getModules();
        if (modules.isEmpty()) return;

        List<Module> renderableModules = updateAnimations(modules);
        if (renderableModules.isEmpty()) return;

        renderUI(event.guiGraphics(), renderableModules);
    }

    private List<Module> updateAnimations(List<Module> modules) {
        List<Module> renderableModules = new ArrayList<>();

        for (Module module : modules) {
            animations.computeIfAbsent(module, k -> new ModuleAnimation());
            ModuleAnimation anim = animations.get(module);

            // 优化：更平滑的非线性缓动 (Easing)
            float targetX = module.enabled ? 0.0f : 100.0f;
            anim.xOffset += (targetX - anim.xOffset) * ANIMATION_SMOOTHNESS;

            // 修复精度问题，防止无限接近但达不到
            if (Math.abs(targetX - anim.xOffset) < 0.5f) anim.xOffset = targetX;

            if (module.enabled || anim.xOffset < 99.0f) {
                renderableModules.add(module);
            }
        }

        animations.keySet().retainAll(modules);

        if (renderableModules.isEmpty()) return renderableModules;

        // 按名称长度降序排序
        renderableModules.sort(Comparator.comparingInt((Module m) -> m.getName().length()).reversed());

        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
        float targetHeight = TOP_PADDING + renderableModules.size() * lineHeight + BOTTOM_PADDING;

        // 高度平滑动画
        animatedHeight += (targetHeight - animatedHeight) * ANIMATION_SMOOTHNESS;
        if (Math.abs(targetHeight - animatedHeight) < 0.5f) animatedHeight = targetHeight;

        // Y轴平滑跟进
        float currentY = TOP_PADDING;
        for (Module module : renderableModules) {
            ModuleAnimation anim = animations.get(module);
            anim.targetY = currentY;

            anim.currentY += (anim.targetY - anim.currentY) * ANIMATION_SMOOTHNESS;
            if (Math.abs(anim.targetY - anim.currentY) < 0.5f) anim.currentY = anim.targetY;

            currentY += lineHeight;
        }

        return renderableModules;
    }

    private void renderUI(GuiGraphicsExtractor gui, List<Module> modules) {
        int startX = 4; // 稍微远离屏幕边缘

        int maxModuleWidth = 0;
        for (Module module : modules) {
            int textWidth = mc.font.width(module.getName());
            int moduleWidth = STATUS_BAR_WIDTH + PADDING_TEXT_LEFT + textWidth + PADDING_TEXT_RIGHT;
            if (moduleWidth > maxModuleWidth) maxModuleWidth = moduleWidth;
        }

        int mainBgWidth = maxModuleWidth + PADDING_MAIN_LEFT + PADDING_MAIN_RIGHT;

        int bgX1 = startX;
        int baseY = 4; // 顶部留白
        int bgX2 = bgX1 + mainBgWidth;
        int bgY2 = (int) (baseY + animatedHeight);

        if (mainBackground.enabled) {
            // 柔和的阴影 (降低透明度至 30%)
            int shadowColor = (SHADOW_COLOR_BASE & 0xFFFFFF) | (0x4D000000);
            gui.fill(bgX1 + SHADOW_OFFSET, baseY + SHADOW_OFFSET, bgX2 + SHADOW_OFFSET, bgY2 + SHADOW_OFFSET, shadowColor);

            // 主背景 (75%透明度)
            int bgColor = (BG_COLOR_BASE & 0xFFFFFF) | (0xBF000000);
            gui.fill(bgX1, baseY, bgX2, bgY2, bgColor);

            // 极细微的顶部高光边框 (提升质感)
            int highlightColor = (BORDER_COLOR_BASE & 0xFFFFFF) | (0x1A000000); // 10% 纯白
            gui.fill(bgX1, baseY, bgX2, baseY + 1, highlightColor);

            // 左边框装饰线
            int borderColor = (BORDER_COLOR_BASE & 0xFFFFFF) | (0x26000000); // 15% 透明度
            gui.fill(bgX1, baseY, bgX1 + 1, bgY2, borderColor);
        }

        for (Module module : modules) {
            renderModule(gui, module, startX, baseY);
        }
    }

    private void renderModule(GuiGraphicsExtractor gui, Module module, int startX, int baseY) {
        ModuleAnimation anim = animations.get(module);
        if (anim == null) return;

        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
        int textWidth = mc.font.width(module.getName());
        int moduleWidth = STATUS_BAR_WIDTH + PADDING_TEXT_LEFT + textWidth + PADDING_TEXT_RIGHT;

        int moduleX = (int) (startX + PADDING_MAIN_LEFT - anim.xOffset);
        int moduleY = (int) (baseY + anim.currentY);
        int moduleBgX2 = moduleX + moduleWidth;
        int moduleBgY2 = moduleY + lineHeight;

        if (moduleBackground.enabled) {
            gui.fill(moduleX, moduleY, moduleBgX2, moduleBgY2, MODULE_BG_COLOR);
        }

        int statusColor = module.enabled
                ? (STATUS_ENABLED_COLOR | 0xFF000000)
                : (STATUS_DISABLED_COLOR | 0xFF000000);

        gui.fill(moduleX, moduleY, moduleX + STATUS_BAR_WIDTH, moduleBgY2, statusColor);

        int textX = moduleX + STATUS_BAR_WIDTH + PADDING_TEXT_LEFT;
        // 修正文本垂直居中公式
        int textY = moduleY + (lineHeight / 2) - (mc.font.lineHeight / 2);

        gui.text(mc.font, module.getName(), textX, textY, TEXT_COLOR_BASE | 0xFF000000, true);
    }
}