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
    private static final float ANIMATION_SPEED = 0.2f; // 略微减慢动画速度，更平滑
    private static final int BACKGROUND_COLOR = 0xAA000000; // 更黑，更透明 (66% 透明度)
    private static final int MODULE_BG_COLOR = 0x1AFFFFFF; // 更低透明度的模块背景，更柔和
    private static final int ACCENT_COLOR = 0xFF00FFFF; // 强调色：霓虹青色
    private static final int ENABLED_DOT_COLOR = 0xFF33FF00; // 启用指示线：霓虹绿色
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LINE_HEIGHT_PADDING = 3; // 新增：垂直间距增加 3 像素

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

        // 按名称长度排序（保持原样，尽管左对齐通常不需要）
        renderableModules.sort(Comparator.comparingInt((Module m) -> m.getName().length()).reversed());

        // 更新高度和Y位置动画
        // 【优化点 1】增加行高
        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
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

            // 【优化点 2】顶部横线保持不变，使用新的强调色
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

        // 重新计算新的高度和垂直居中偏移
        int lineHeight = mc.font.lineHeight + LINE_HEIGHT_PADDING;
        int bgHeight = lineHeight - 1;
        int textYOffset = (lineHeight - mc.font.lineHeight) / 2;

        String text = module.getName();
        int textWidth = mc.font.width(text);

        // 默认文本 X 坐标：起始 X (2) + 4 像素内边距
        int baseRenderX = startX + 4;
        int renderX = (int) (baseRenderX - anim.xOffset);
        int y = (int) anim.currentY;

        // 渲染模块背景
        if (moduleBackground.enabled) {
            int bgX = (int) (startX + 2 - anim.xOffset);
            int bgWidth = textWidth + 6;

            int bgAlpha = module.enabled ? 0x40 : 0x20;
            int moduleBgColor = (MODULE_BG_COLOR & 0x00FFFFFF) | (bgAlpha << 24);

            gui.fill(bgX, y, bgX + bgWidth, y + bgHeight, moduleBgColor);
        }

        // 渲染启用指示线（替换指示点）
        if (module.enabled) {
            int lineThickness = 1;
            // 【优化点 3】细线位于列表的最左侧 startX=2
            int lineX = startX;
            int lineY = y;
            // 细线的高度与模块背景高度一致
            gui.fill(lineX, lineY, lineX + lineThickness, lineY + bgHeight, ENABLED_DOT_COLOR);

            // 启用后，将文本和背景整体向右移动 2 像素，与指示线错开
            renderX = (int) (baseRenderX + 2 - anim.xOffset);
        } else {
            // 未启用，保持默认的左对齐位置
            renderX = (int) (baseRenderX - anim.xOffset);
        }

        // 渲染文本
        // 【优化点 4】增加垂直居中偏移，使文本居中
        gui.drawString(mc.font, text, renderX, y + textYOffset, TEXT_COLOR, true);
    }
}