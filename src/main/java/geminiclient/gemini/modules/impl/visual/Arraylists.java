package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

public class Arraylists extends Module {
    // 配置选项
    public final BoolValue mainBackground = new BoolValue("Main Bkgrd", true);
    public final BoolValue moduleBackground = new BoolValue("Module Bkgrd", true);

    // 样式常量（与通知组件统一）
    private static final int BG_COLOR_BASE = 0x1E1E1E;          // 深灰色
    private static final int SHADOW_COLOR_BASE = 0x000000;      // 阴影黑色
    private static final int BORDER_COLOR_BASE = 0xFFFFFF;      // 边框白色
    private static final int TEXT_COLOR_BASE = 0xFFFFFF;        // 文字白色
    private static final int STATUS_ENABLED_COLOR = 0x4CAF50;   // 绿色（启用）
    private static final int STATUS_DISABLED_COLOR = 0xF44336;  // 红色（禁用，用于动画退出中的模块）
    private static final int MODULE_BG_COLOR = 0x33000000;      // 模块背景色（半透明黑）

    // 尺寸常量
    private static final int STATUS_BAR_WIDTH = 3;              // 状态条宽度
    private static final int PADDING_TEXT_LEFT = 4;             // 状态条右侧到文本的距离
    private static final int PADDING_TEXT_RIGHT = 4;            // 文本右侧到模块背景边缘的距离
    private static final int PADDING_MAIN_LEFT = 2;             // 主背景左边缘到模块左边缘的距离
    private static final int PADDING_MAIN_RIGHT = 2;            // 主背景右边缘留白
    private static final int TOP_PADDING = 3;                    // 主背景顶部内边距
    private static final int BOTTOM_PADDING = 3;                 // 主背景底部内边距
    private static final int LINE_HEIGHT_PADDING = 3;            // 行高额外间距
    private static final int SHADOW_OFFSET = 2;                  // 阴影偏移量

    // 动画常量
    private static final float ANIMATION_SPEED = 0.2f;

    // 模块动画状态
    private static class ModuleAnimation {
        float xOffset = 100.0f;
        float targetY = 0;
        float currentY = 0;
    }

    private final Map<Module, ModuleAnimation> animations = new HashMap<>();
    private float animatedHeight = 0; // 主背景动画高度

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

    /**
     * 更新所有模块的动画状态，并返回需要渲染的模块列表
     */
    private List<Module> updateAnimations(List<Module> modules) {
        List<Module> renderableModules = new ArrayList<>();

        for (Module module : modules) {
            animations.computeIfAbsent(module, k -> new ModuleAnimation());
            ModuleAnimation anim = animations.get(module);

            // X轴动画：启用时滑入（xOffset -> 0），禁用时滑出（xOffset -> 100）
            float targetX = module.enabled ? 0.0f : 100.0f;
            float diffX = targetX - anim.xOffset;
            anim.xOffset += diffX * ANIMATION_SPEED;
            if (Math.abs(diffX) < 0.1f) anim.xOffset = targetX;

            // 只有启用或动画未完全滑出的模块才需要渲染
            if (module.enabled || anim.xOffset < 99.0f) {
                renderableModules.add(module);
            }
        }

        // 清理已经不存在的模块
        animations.keySet().retainAll(modules);

        if (renderableModules.isEmpty()) return renderableModules;

        // 按名称长度降序排序（原逻辑，可保持）
        renderableModules.sort(Comparator.comparingInt((Module m) -> m.getName().length()).reversed());

        // 计算总高度并更新Y轴动画
        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
        float targetHeight = TOP_PADDING + renderableModules.size() * lineHeight + BOTTOM_PADDING;

        float diffH = targetHeight - animatedHeight;
        animatedHeight += diffH * ANIMATION_SPEED;
        if (Math.abs(diffH) < 0.1f) animatedHeight = targetHeight;

        // 更新每个模块的目标Y位置并执行Y轴动画
        float currentY = TOP_PADDING;
        for (Module module : renderableModules) {
            ModuleAnimation anim = animations.get(module);
            anim.targetY = currentY;

            float diffY = anim.targetY - anim.currentY;
            anim.currentY += diffY * ANIMATION_SPEED;
            if (Math.abs(diffY) < 0.1f) anim.currentY = anim.targetY;

            currentY += lineHeight;
        }

        return renderableModules;
    }

    /**
     * 渲染整个列表
     */
    private void renderUI(GuiGraphics gui, List<Module> modules) {
        int startX = 2; // 距离屏幕左边缘的距离

        // 计算每个模块的宽度（含状态条和内边距），并取最大值
        int maxModuleWidth = 0;
        for (Module module : modules) {
            int textWidth = mc.font.width(module.getName());
            int moduleWidth = STATUS_BAR_WIDTH + PADDING_TEXT_LEFT + textWidth + PADDING_TEXT_RIGHT;
            if (moduleWidth > maxModuleWidth) maxModuleWidth = moduleWidth;
        }

        // 主背景宽度 = 最大模块宽度 + 左右外边距
        int mainBgWidth = maxModuleWidth + PADDING_MAIN_LEFT + PADDING_MAIN_RIGHT;

        // 计算主背景矩形坐标
        int bgX1 = startX;
        int bgY1 = 0; // 实际渲染时从屏幕顶部开始？需要根据动画高度计算 Y 起始位置
        // 注意：Y 坐标需要动态确定，因为列表是从屏幕顶部开始往下渲染，通常 Arraylists 固定在屏幕左上角。
        // 这里我们假定列表始终从屏幕顶部开始，即 bgY1 = 0。
        // 但为了美观，可以稍微下移，比如 bgY1 = 2，原代码中背景是从 y=2 开始的。
        // 我们保持原风格：主背景 Y 从 2 开始。
        int baseY = 2;
        int bgX2 = bgX1 + mainBgWidth;
        int bgY2 = (int) (baseY + animatedHeight);

        // 绘制主背景（如果启用）
        if (mainBackground.enabled) {
            // 阴影（偏移2像素）
            int shadowColor = (SHADOW_COLOR_BASE & 0xFFFFFF) | (0x80000000); // 50% 透明度黑色
            gui.fill(bgX1 + SHADOW_OFFSET, baseY + SHADOW_OFFSET, bgX2 + SHADOW_OFFSET, bgY2 + SHADOW_OFFSET, shadowColor);

            // 主背景（80%透明度深灰色）
            int bgColor = (BG_COLOR_BASE & 0xFFFFFF) | (0xCC000000);
            gui.fill(bgX1, baseY, bgX2, bgY2, bgColor);

            // 细边框（半透明白色，20%透明度）
            int borderColor = (BORDER_COLOR_BASE & 0xFFFFFF) | (0x33000000);
            // 上
            gui.fill(bgX1, baseY, bgX2, baseY + 1, borderColor);
            // 下
            gui.fill(bgX1, bgY2 - 1, bgX2, bgY2, borderColor);
            // 左
            gui.fill(bgX1, baseY, bgX1 + 1, bgY2, borderColor);
            // 右
            gui.fill(bgX2 - 1, baseY, bgX2, bgY2, borderColor);
        }

        // 渲染每个模块
        for (Module module : modules) {
            renderModule(gui, module, startX, baseY);
        }
    }

    /**
     * 渲染单个模块
     */
    private void renderModule(GuiGraphics gui, Module module, int startX, int baseY) {
        ModuleAnimation anim = animations.get(module);
        if (anim == null) return;

        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
        int textWidth = mc.font.width(module.getName());

        // 模块的总宽度（与计算 maxModuleWidth 时一致）
        int moduleWidth = STATUS_BAR_WIDTH + PADDING_TEXT_LEFT + textWidth + PADDING_TEXT_RIGHT;

        // 模块的 X 位置：主背景左边缘 + 左外边距，再根据动画偏移
        int moduleX = (int) (startX + PADDING_MAIN_LEFT - anim.xOffset);
        int moduleY = (int) (baseY + anim.currentY);
        int moduleBgX2 = moduleX + moduleWidth;
        int moduleBgY2 = moduleY + lineHeight;

        // 模块背景（如果启用）
        if (moduleBackground.enabled) {
            // 半透明黑色背景
            gui.fill(moduleX, moduleY, moduleBgX2, moduleBgY2, MODULE_BG_COLOR);
        }

        // 状态条（绿色 = 启用，红色 = 禁用但仍在动画）
        int statusColor;
        if (module.enabled) {
            statusColor = STATUS_ENABLED_COLOR | 0xFF000000; // 完全不透明
        } else {
            statusColor = STATUS_DISABLED_COLOR | 0xFF000000;
        }
        // 状态条位于模块背景左侧内部，宽度 STATUS_BAR_WIDTH，高度与模块背景相同
        gui.fill(moduleX, moduleY, moduleX + STATUS_BAR_WIDTH, moduleBgY2, statusColor);

        // 文本位置：状态条右侧 + 左内边距
        int textX = moduleX + STATUS_BAR_WIDTH + PADDING_TEXT_LEFT;
        int textY = moduleY + (lineHeight - mc.font.lineHeight) / 2; // 垂直居中

        // 绘制文本（带阴影）
        gui.drawString(mc.font, module.getName(), textX, textY, TEXT_COLOR_BASE | 0xFF000000, true);
    }
}