package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

import java.util.Comparator;
import java.util.List;

public class Arraylists extends Module {
    // 保持 ANIMATION_SPEED 不变，可能需要调整来得到更平滑的效果
    private static final float ANIMATION_SPEED = 0.15f; // 稍降一点速度可能会更平滑

    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public static void render2d(Render2DEvent event) {
        // 1. 获取所有模块
        List<Module> modules = Gemini.moduleManager.getModules();

        // --- 动画更新逻辑 (负责X轴进出) ---
        // 模块列表应该以一个稳定的顺序遍历，例如按字母顺序，以确保 Y 轴计算稳定
        modules.sort(Comparator.comparing(Module::getName));

        for (Module module : modules) {
            // 目标位置：开启为 0 (内), 关闭为 100 (外)
            float targetOffset = module.enabled ? 0.0f : 100.0f;

            // 使用线性插值 (Lerp) 平滑地向目标移动
            float diff = targetOffset - module.animationXOffset;
            module.animationXOffset += diff * ANIMATION_SPEED;

            // 确保完全禁用时动画停止，避免不必要的计算
            if (!module.enabled && module.animationXOffset > 99.9f) {
                module.animationXOffset = 100.0f;
            }
        }

        // --- 绘制逻辑 (负责Y轴堆叠) ---

        // 2. 筛选出需要绘制的模块 (已启用 或 正在滑入/滑出)
        // 并且按照名称长度从长到短排序 (确保右对齐，如果需要的话)
        List<Module> renderableModules = modules.stream()
                .filter(module -> module.enabled || module.animationXOffset < 99.0f) // 正在动画中的也要保留
                .sorted(Comparator.comparingInt((Module module) -> module.getName().length()).reversed()) // 重新排序以进行绘制
                .toList();


        int startX = 2;
        int startY = 2;
        int lineHeight = mc.font.lineHeight + 1;
        int currentColor = 0xFFFFFFFF; // 白色文本

        int lineIndex = 0; // 用于计算Y轴位置的行索引

        // 3. 遍历并绘制需要渲染的模块
        for (Module module : renderableModules) {
            String text = module.getName();

            // 计算当前 Module 的 Y 位置
            // Y 轴位置仅由在 renderableModules 列表中的行索引决定
            int currentY = startY + lineIndex * lineHeight;

            // 将动画偏移量应用于 X 轴
            // 如果 Arraylist 位于左上角，且想实现从左向右滑入：renderX = startX - animationXOffset
            // 如果 Arraylist 位于右上角，且想实现从右向左滑入：renderX = startX + module.getWidth() - animationXOffset (假设 startX 是屏幕右边缘)
            int renderX = (int) (startX - module.animationXOffset); // 保持你原来的逻辑

            // 使用 GuiGraphics.drawString 绘制文本
            event.guiGraphics().drawString(mc.font, text, renderX, currentY, currentColor, true);

            // 仅对绘制的行增加行索引
            lineIndex++;
        }
    }
}