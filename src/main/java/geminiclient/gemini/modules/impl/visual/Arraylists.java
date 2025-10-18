package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

import java.util.Comparator;
import java.util.List;

public class Arraylists extends Module {
    // 使用更平滑的动画速度
    private static final float ANIMATION_SPEED = 0.2f;
    // 动画完成阈值，避免不必要的计算
    private static final float ANIMATION_THRESHOLD = 0.1f;
    // 完全隐藏的阈值
    private static final float HIDDEN_THRESHOLD = 99.0f;

    // 缓存上一次的模块列表，减少排序次数
    private List<Module> lastSortedModules;
    private static boolean needsResort = true;

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void render2d(Render2DEvent event) {
        List<Module> modules = Gemini.moduleManager.getModules();

        // 只在模块状态变化或首次渲染时重新排序
        if (needsResort || lastSortedModules == null) {
            modules.sort(Comparator.comparing(Module::getName));
            lastSortedModules = List.copyOf(modules); // 创建不可变副本
            needsResort = false;
        }

        // 动画更新逻辑
        updateAnimations(modules);

        // 绘制逻辑
        renderModuleList(event, modules);
    }

    /**
     * 更新所有模块的动画状态
     */
    private void updateAnimations(List<Module> modules) {
        for (Module module : modules) {
            float targetOffset = module.enabled ? 0.0f : 100.0f;
            float currentOffset = module.animationXOffset;

            // 检查是否需要更新动画
            if (Math.abs(targetOffset - currentOffset) < ANIMATION_THRESHOLD) {
                module.animationXOffset = targetOffset;
                continue;
            }

            // 使用缓动函数让动画更平滑
            float diff = targetOffset - currentOffset;
            module.animationXOffset += diff * ANIMATION_SPEED;

            // 标记需要重新排序，因为动画状态改变了渲染顺序
            if (Math.abs(diff) > ANIMATION_THRESHOLD) {
                needsResort = true;
            }

            // 完全隐藏时停止精确计算
            if (!module.enabled && currentOffset > HIDDEN_THRESHOLD) {
                module.animationXOffset = 100.0f;
            }
        }
    }

    /**
     * 渲染模块列表
     */
    private void renderModuleList(Render2DEvent event, List<Module> modules) {
        int startX = 2;
        int startY = 2;
        int lineHeight = mc.font.lineHeight + 1;
        int currentColor = 0xFFFFFFFF;

        int lineIndex = 0;

        for (Module module : modules) {
            // 跳过完全隐藏的模块
            if (!module.enabled && module.animationXOffset >= HIDDEN_THRESHOLD) {
                continue;
            }

            String text = module.getName();
            int currentY = startY + lineIndex * lineHeight;

            // 应用缓动动画到X轴位置
            int renderX = calculateRenderX(startX, module.animationXOffset);

            event.guiGraphics().drawString(mc.font, text, renderX, currentY, currentColor, true);
            lineIndex++;
        }
    }

    /**
     * 计算渲染X坐标，添加缓动效果
     */
    private int calculateRenderX(int startX, float animationOffset) {
        // 使用缓动函数让进出动画更自然
        float easedOffset = easeOutCubic(animationOffset / 100.0f) * 100.0f;
        return (int) (startX - easedOffset);
    }

    /**
     * 缓动函数：easeOutCubic，提供更自然的动画效果
     */
    private float easeOutCubic(float x) {
        return (float) (1 - Math.pow(1 - x, 3));
    }

    /**
     * 当模块状态改变时调用此方法，触发重新排序
     */
    public static void onModuleStateChange() {
        needsResort = true;
    }
}