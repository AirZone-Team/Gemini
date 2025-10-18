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

    // 动画和样式常量
    private static final float ANIMATION_SPEED = 0.25f;
    private static final int BACKGROUND_COLOR = 0x80101010;
    private static final int MODULE_BG_COLOR = 0x30FFFFFF;
    private static final int ACCENT_COLOR = 0xFF64B5F6;
    private static final int ENABLED_DOT_COLOR = 0xFFFFA500;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

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
        if (modules.isEmpty())
            return;

        List<Module> renderableModules = updateAnimations(modules);
        if (renderableModules.isEmpty())
            return;

        renderUI(event.guiGraphics(), renderableModules);
    }

    private List<Module> updateAnimations(List<Module> modules) {
        // 筛选需要渲染的模块
        List<Module> renderableModules = new ArrayList<>();
        for (Module module : modules) {
            if (!animations.containsKey(module)) {
                animations.put(module, new ModuleAnimation());
            }

            ModuleAnimation anim = animations.get(module);

            // 更新X轴动画
            float targetX = module.enabled ? 0.0f : 100.0f;
            float diffX = targetX - anim.xOffset;
            anim.xOffset += diffX * ANIMATION_SPEED;

            // 精确对齐完成动画
            if (Math.abs(diffX) < 0.1f)
                anim.xOffset = targetX;

            // 筛选可见模块
            if (module.enabled || anim.xOffset < 99.0f) {
                renderableModules.add(module);
            }
        }

        // 清理不存在的模块
        animations.keySet().retainAll(modules);

        if (renderableModules.isEmpty())
            return renderableModules;

        // 按名称长度排序
        renderableModules.sort(Comparator.comparingInt((Module m) -> m.getName().length()).reversed());

        // 更新高度和Y位置动画
        int lineHeight = mc.font.lineHeight + 1;
        float targetHeight = renderableModules.size() * lineHeight + 3; // 增加1像素高度

        // 高度动画
        float diffH = targetHeight - animatedHeight;
        animatedHeight += diffH * ANIMATION_SPEED;
        if (Math.abs(diffH) < 0.1f)
            animatedHeight = targetHeight;

        // 更新每个模块的Y位置
        float currentY = 3; // 从3开始而不是2，下移1像素
        for (Module module : renderableModules) {
            ModuleAnimation anim = animations.get(module);
            anim.targetY = currentY;

            float diffY = anim.targetY - anim.currentY;
            anim.currentY += diffY * ANIMATION_SPEED;
            if (Math.abs(diffY) < 0.1f)
                anim.currentY = anim.targetY;

            currentY += lineHeight;
        }

        return renderableModules;
    }

    private void renderUI(GuiGraphics gui, List<Module> modules) {
        int startX = 2;

        // 计算最大宽度
        int maxWidth = modules.stream()
                .mapToInt(m -> mc.font.width(m.getName()))
                .max().orElse(0) + 8;

        // 渲染主背景
        if (mainBackground.enabled) {
            // 背景从2开始，高度增加1像素
            gui.fill(startX, 2, startX + maxWidth + 4, 2 + (int) animatedHeight, BACKGROUND_COLOR);
            // 顶部横线保持在2-3位置
            gui.fill(startX, 2, startX + maxWidth + 4, 3, ACCENT_COLOR);
        }

        // 渲染每个模块
        for (Module module : modules) {
            renderModule(gui, module, startX);
        }
    }

    private void renderModule(GuiGraphics gui, Module module, int startX) {
        ModuleAnimation anim = animations.get(module);
        if (anim == null)
            return;

        String text = module.getName();
        int renderX = (int) (startX + 4 - anim.xOffset);
        int y = (int) anim.currentY; // 已经下移1像素
        int textWidth = mc.font.width(text);

        // 渲染模块背景
        if (moduleBackground.enabled) {
            int bgX = (int) (startX + 2 - anim.xOffset);
            int bgWidth = textWidth + 6;
            int bgHeight = mc.font.lineHeight - 1;
            int bgAlpha = module.enabled ? 0x40 : 0x20;
            int moduleBgColor = (MODULE_BG_COLOR & 0x00FFFFFF) | (bgAlpha << 24);

            gui.fill(bgX, y, bgX + bgWidth, y + bgHeight, moduleBgColor);
        }

        // 渲染启用指示点
        if (module.enabled) {
            int dotSize = 2;
            int dotX = renderX - 6;
            int dotY = y + mc.font.lineHeight / 2 - dotSize / 2;
            gui.fill(dotX, dotY, dotX + dotSize, dotY + dotSize, ENABLED_DOT_COLOR);
        }

        // 渲染文本
        gui.drawString(mc.font, text, renderX, y, TEXT_COLOR, true);
    }
}