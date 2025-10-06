package geminiclient.gemini.modules.impl.visual;

import com.cubk.event.annotations.EventTarget;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;

import java.util.Comparator;
import java.util.List;

public class Arraylists extends Module {
    private static final float ANIMATION_SPEED = 0.2f;
    public Arraylists() {
        super("Arraylists", ModuleEnum.Visual);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public static void render2d(Render2DEvent event) {
        // 3. 遍历并绘制 Module 名称
        for (Module module : Gemini.moduleManager.getModules()) {
            float targetOffset = module.enabled ? 0.0f : 100.0f; // 目标位置：开启为 0 (内), 关闭为 100 (外)

            // 使用线性插值 (Lerp) 平滑地向目标移动
            float diff = targetOffset - module.animationXOffset;
            module.animationXOffset += diff * ANIMATION_SPEED;

            // 如果模块已关闭且动画完成，则停止更新
            if (!module.enabled && module.animationXOffset > 99.9f) {
                module.animationXOffset = 100.0f;
            }
        }

        List<Module> modules = Gemini.moduleManager.getModules();

        // 排序规则：按名称长度从长到短
        modules.sort(Comparator.comparingInt((Module module) -> module.getName().length()).reversed());

        int startX = 2;
        int startY = 2;
        int lineHeight = mc.font.lineHeight + 1;
        int currentColor = 0xFFFFFFFF; // 白色文本

        int lineIndex = 0; // 用于计算Y轴位置的行索引

        // 2. 遍历 Module 并绘制
        for (Module module : modules) {
            // 关键：只有当 Module 正在动画中 (不在屏幕外) 或者 Module 已经启用时才绘制
            // 检查 Module.animationXOffset < 99.0f 确保即使 Module 刚刚禁用，动画也会完整播放
            if (module.enabled || module.animationXOffset < 99.0f) {

                String text = module.getName();

                // 计算当前 Module 的 X 和 Y 位置
                int currentY = startY + lineIndex * lineHeight;

                // 将动画偏移量应用于 X 轴
                int renderX = (int) (startX - module.animationXOffset);

                // 使用 GuiGraphics.drawString 绘制文本
                event.guiGraphics().drawString(mc.font, text, renderX, currentY, currentColor, true);

                // 仅对绘制的行增加行索引
                lineIndex++;
            }
        }
    }
}
