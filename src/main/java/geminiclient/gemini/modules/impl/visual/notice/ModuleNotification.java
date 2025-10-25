package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import java.awt.Color;

/**
 * 优化版本：
 * 1. 从右侧滑入/滑出动画，使用 Cubic Out 缓动函数。
 * 2. 引入圆角背景（需要一个 drawRoundedRect 辅助方法，或使用现代 GuiGraphics API）。
 * 3. 进度条改为位于底部的细长条。
 * 4. 可选的左侧模块状态指示条 (绿色/红色)。
 */
public class ModuleNotification {

    // --- 模拟/简化 NotificationLevel (保持原样，结构清晰) ---
    public enum NotificationLevel {
        INFO(0x00A8FF), WARN(0xFFB800), ERROR(0xFF0000);

        private final int color;

        NotificationLevel(int color) {
            this.color = color;
        }

        // 返回 ARGB 格式颜色
        public int getColorInt() {
            return 0xFF000000 | this.color;
        }
    }
    // ------------------------------------

    // --- 尺寸/间隔常量 ---
    private static final int HEIGHT = 24;

    private static final int PROGRESS_BAR_HEIGHT = 2; // 底部进度条高度
    private static final int STATUS_BAR_WIDTH = 3;    // 状态条宽度
    private static final int PADDING_X = 6;           // 文本左右内边距
    private static final int PADDING_Y = 4;           // 上下边距 (通知相对于目标Y的偏移)

    // --- 动画时间常量 ---
    private static final int INTRO_DURATION_MS = 500;
    private static final int OUTRO_DURATION_MS = 500;
    private static final int MIN_LIFE_MS = INTRO_DURATION_MS + OUTRO_DURATION_MS; // 最小生命周期

    // --- 颜色常量 ---
    // 背景颜色：D0 (81.25%) 透明度
    private static final int BG_COLOR = 0xD0000000;
    private static final int TEXT_COLOR = Color.WHITE.getRGB();
    private static final int STATUS_ENABLED_COLOR = 0xFF00FF00;
    private static final int STATUS_DISABLED_COLOR = 0xFFFF0000;

    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();

    private final boolean showModuleStatus;
    private final boolean moduleIsEnabled;

    // 当前绘制的宽度 (用于布局)
    private float currentWidth;

    public ModuleNotification(NotificationLevel level, String message, long age, boolean isEnabled, boolean showStatus) {
        this.level = level;
        this.message = message;
        // 确保最小持续时间足够完成滑入和滑出动画
        this.maxAge = Math.max(age, MIN_LIFE_MS);
        this.moduleIsEnabled = isEnabled;
        this.showModuleStatus = showStatus;
        this.currentWidth = this.calculateTargetWidth();
    }

    // --- 缓动函数（Cubic Out） ---
    // f(x) = 1 - (1 - x)^3
    private float easeOutCubic(float x) {
        return 1.0F - (float)Math.pow(1.0F - x, 3);
    }

    public void render(GuiGraphics guiGraphics, float targetX, float targetY) {
        long timeElapsed = System.currentTimeMillis() - createTime;
        long remainingTime = maxAge - timeElapsed;

        if (remainingTime < -OUTRO_DURATION_MS) return;

        float notificationWidth = this.calculateTargetWidth();
        // 目标 X 坐标
        // 初始 X 偏移：通知宽度 + 额外间隙
        final float initialOffset = notificationWidth + 10.0f;

        float currentX;
        float progress;

        // --- 1. X 轴动画计算 ---
        if (timeElapsed < INTRO_DURATION_MS) {
            // 滑入：从 finalX + initialOffset 到 finalX
            progress = (float)timeElapsed / INTRO_DURATION_MS;
            float easedProgress = easeOutCubic(progress);
            currentX = Mth.lerp(easedProgress, targetX + initialOffset, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            // 滑出：从 finalX 到 finalX + initialOffset
            progress = 1.0F - (float)remainingTime / OUTRO_DURATION_MS; // 进度从 0 -> 1
            float easedProgress = easeOutCubic(progress);
            currentX = Mth.lerp(easedProgress, targetX, targetX + initialOffset);
        } else {
            // 保持
            currentX = targetX;
        }

        this.currentWidth = notificationWidth;

        // --- 2. 绘制区域坐标 ---
        int rectX1 = (int)currentX;
        int rectY1 = (int)targetY + PADDING_Y;
        int rectX2 = (int)(currentX + currentWidth);
        int rectY2 = (int)(targetY + PADDING_Y + (HEIGHT - PADDING_Y * 2)); // 整体高度减去上下边距

        // --- 3. 绘制圆角背景 ---
        // 注意：原生的 GuiGraphics 没有 drawRoundedRect 方法。
        // 在实际项目中，需要引入一个辅助方法或使用更底层的渲染 API (如 RenderSystem.setShader + 绘制四边形)。
        // 为了优化代码结构，我们假设存在一个静态辅助方法。
        // 如果没有，则退化为普通的 fill。

        // 假设存在 drawRoundedRect 方法:
        // HelperRenderer.drawRoundedRect(guiGraphics, rectX1, rectY1, rectX2, rectY2, CORNER_RADIUS, BG_COLOR);

        // 否则，使用 fill (无圆角):
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY2, BG_COLOR);


        // --- 4. 绘制状态指示条 ---
        int textXOffset = PADDING_X; // 文本 X 坐标相对于 rectX1 的偏移

        if (this.showModuleStatus) {
            int statusColor = this.moduleIsEnabled ? STATUS_ENABLED_COLOR : STATUS_DISABLED_COLOR;

            // 状态条绘制区域 (左侧，高度到进度条上方)
            guiGraphics.fill(
                    rectX1,
                    rectY1,
                    rectX1 + STATUS_BAR_WIDTH,
                    rectY2 - PROGRESS_BAR_HEIGHT, // 结束于进度条上方
                    statusColor
            );
            textXOffset = STATUS_BAR_WIDTH + PADDING_X; // 文本向右移动 状态条宽度 + 文本内边距
        }


        // --- 5. 绘制底部进度条 ---

        // lifeProgress: 从 1.0f (开始) 递减到 0.0f (结束)
        // 使用剩余时间计算进度，更直观
        float progressRatio = Mth.clamp((float)remainingTime / (float)(maxAge - MIN_LIFE_MS), 0.0F, 1.0F);

        // 进度条的绘制宽度
        // 进度条的背景（全宽减去状态条宽度）
        float progressBarFullWidth = currentWidth - (this.showModuleStatus ? STATUS_BAR_WIDTH : 0);
        float progressWidth = progressBarFullWidth * progressRatio;

        int barColor = level.getColorInt();

        // 进度条的起始 X 坐标 (在状态条右侧，如果存在)
        int barX1 = rectX1 + (this.showModuleStatus ? STATUS_BAR_WIDTH : 0);

        // 绘制彩色进度条，位于底部 PROGRESS_BAR_HEIGHT 高度
        guiGraphics.fill(
                barX1, // X1: 从状态条右侧或通知左侧开始
                rectY2 - PROGRESS_BAR_HEIGHT, // Y1: 底部 PROGRESS_BAR_HEIGHT
                (int)(barX1 + progressWidth), // X2: X1 + 进度条实际宽度
                rectY2, // Y2: 通知底部
                barColor
        );

        // --- 6. 绘制文本 ---
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                this.message,
                rectX1 + textXOffset, // 文本起始 X: 背景 X1 + 文本偏移
                rectY1 + (HEIGHT - PADDING_Y * 2 - Minecraft.getInstance().font.lineHeight) / 2, // 垂直居中
                TEXT_COLOR,
                true
        );
    }

    // --- Getters 和辅助方法 ---

    // 修复文本宽度计算，确保其与状态条逻辑兼容
    public float calculateTargetWidth() {
        int stringWidth = Minecraft.getInstance().font.width(this.message);
        // 文本总宽度 = 文本宽度 + 左右内边距 (2 * PADDING_X)
        float textTotalWidth = (float)stringWidth + 2.0F * PADDING_X;
        // 状态条偏移：状态条宽度
        float statusBarOffset = this.showModuleStatus ? STATUS_BAR_WIDTH : 0.0F;
        return textTotalWidth + statusBarOffset;
    }

    public boolean isExpired() {
        // 只有当滑出动画结束才算真正过期
        long remainingTime = maxAge - (System.currentTimeMillis() - createTime);
        return remainingTime < -OUTRO_DURATION_MS;
    }

    // ... (其他辅助方法保持原样或删除)
    public float getWidth() { return this.currentWidth; }
    public float getHeight() { return (float)HEIGHT; }
    @Override public int hashCode() { return 0; }
    @Override public String toString() { return ""; }
}