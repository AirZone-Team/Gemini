package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import java.awt.Color;

/**
 * 优化版本：
 * 1. 从右侧滑入/滑出动画，使用 Cubic Out 缓动函数。
 * 2. 引入圆角背景。
 * 3. 进度条改为位于底部的细长条。
 * 4. 可选的左侧模块状态指示条 (绿色/红色)。
 */
public class ModuleNotification {

    // --- 模拟/简化 NotificationLevel ---
    public enum NotificationLevel {
        INFO(0x00A8FF), WARN(0xFFB800), ERROR(0xFF0000);

        private final int color;

        NotificationLevel(int color) {
            this.color = color;
        }

        public int getColorInt() {
            // 返回 ARGB 格式颜色
            return 0xFF000000 | this.color;
        }
    }
    // ------------------------------------

    private static final int DEFAULT_DURATION_MS = 3000;
    private static final int HEIGHT = 24;

    // --- 动画时间常量 ---
    private static final int INTRO_DURATION_MS = 500;
    private static final int OUTRO_DURATION_MS = 500;

    // --- 颜色常量 ---
    // 静态背景颜色，增加透明度 (原 0xAA000000, 优化为 0xD0000000)
    private static final int STATIC_BG_COLOR = 0xD0000000;
    private static final int TEXT_COLOR = Color.WHITE.getRGB();
    private static final int STATUS_ENABLED_COLOR = 0xFF00FF00;
    private static final int STATUS_DISABLED_COLOR = 0xFFFF0000;
    private static final int STATUS_BAR_WIDTH = 3;
    private static final float CORNER_RADIUS = 3.0F; // 新增：圆角半径
    private static final int PROGRESS_BAR_HEIGHT = 2; // 新增：底部进度条高度


    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();

    private final boolean showModuleStatus;
    private final boolean moduleIsEnabled;

    private float currentWidth;

    public ModuleNotification(NotificationLevel level, String message, long age, boolean isEnabled, boolean showStatus) {
        this.level = level;
        this.message = message;
        // 确保最小持续时间足够完成滑入和滑出动画
        this.maxAge = Math.max(age, (long)INTRO_DURATION_MS + OUTRO_DURATION_MS);
        this.moduleIsEnabled = isEnabled;
        this.showModuleStatus = showStatus;
        this.currentWidth = this.calculateTargetWidth();
    }

    public ModuleNotification(NotificationLevel level, String message, long age) {
        this(level, message, age, false, false);
    }

    public ModuleNotification(NotificationLevel level, String message) {
        this(level, message, DEFAULT_DURATION_MS, false, false);
    }

    // --- 缓动函数（Cubic Out） ---
    // Mth 中没有标准的缓动，我们使用 Math.pow(x, 3) 模拟三次缓出
    private float easeOutCubic(float x) {
        // f(x) = 1 - (1 - x)^3
        return 1.0F - (float)Math.pow(1.0F - x, 3);
    }

    public void render(GuiGraphics guiGraphics, float targetX, float targetY) {
        long timeElapsed = System.currentTimeMillis() - createTime;
        long remainingTime = maxAge - timeElapsed;

        if (remainingTime < -OUTRO_DURATION_MS) return;

        // ... (省略 X 轴动画计算，保持 currentX 的声明)
        float currentX;
        // ... (动画计算)

        float notificationWidth = this.calculateTargetWidth();
        // 偏移距离略大于通知宽度
        float offsetDistance = notificationWidth + 10.0f;
        float progress;

        if (timeElapsed < INTRO_DURATION_MS) {
            // 滑入：使用缓动
            progress = (float)timeElapsed / INTRO_DURATION_MS;
            float easedProgress = easeOutCubic(progress);
            currentX = Mth.lerp(easedProgress, targetX + offsetDistance, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            // 滑出：使用缓动
            progress = (float)remainingTime / OUTRO_DURATION_MS;
            float easedProgress = easeOutCubic(progress);
            currentX = Mth.lerp(easedProgress, targetX + offsetDistance, targetX);
        } else {
            // 保持
            currentX = targetX;
        }

        this.currentWidth = notificationWidth;

        // --- 2. 坐标计算 ---
        int rectX1 = (int)currentX + 2;
        int rectY1 = (int)targetY + 4;
        int rectX2 = (int)(currentX + 2 + currentWidth);
        int rectY2 = (int)(targetY + 4 + 20);

        // --- 3. 绘制圆角背景 ---
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY2, STATIC_BG_COLOR);

        // --- 4. 绘制状态指示条 ---
        int textOffset = 0;

        if (this.showModuleStatus) {
            int statusColor = this.moduleIsEnabled ? STATUS_ENABLED_COLOR : STATUS_DISABLED_COLOR;

            guiGraphics.fill(
                    rectX1,
                    rectY1,
                    rectX1 + STATUS_BAR_WIDTH,
                    rectY2 - PROGRESS_BAR_HEIGHT,
                    statusColor
            );
            textOffset = STATUS_BAR_WIDTH;
        }


        // --- 5. 绘制底部进度条 (修复逻辑) ---

        // lifeProgress: 从 0.0f (开始) 到 1.0f (结束)
        float lifeProgress = (float)timeElapsed / (float)maxAge;
        // 进度条的实际宽度：从 100% 递减到 0%
        float progressWidth = currentWidth * (1.0f - lifeProgress); //

        int barColor = level.getColorInt();

        // 进度条的起始 X 坐标 (在状态条右侧，如果存在)
        int barX1 = rectX1 + textOffset;

        // 绘制彩色进度条，位于底部 2px 高度
        guiGraphics.fill(
                barX1, // X1: 从状态条右侧开始
                rectY2 - PROGRESS_BAR_HEIGHT, // Y1: 底部 2px
                (int)(barX1 + progressWidth), // X2: 修复! X1 + 进度条实际宽度
                rectY2, // Y2: 通知底部
                barColor
        );

        // --- 6. 绘制文本 ---
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                this.message,
                (int)currentX + 6 + textOffset,
                (int)targetY + 9,
                TEXT_COLOR,
                true
        );
    }

    // --- Getters 和辅助方法 ---

    // 修复文本宽度计算，确保其与状态条逻辑兼容
    public float calculateTargetWidth() {
        int stringWidth = Minecraft.getInstance().font.width(this.message);
        // 12.0F 是文本左右两侧的 padding (6px + 6px)
        // 状态条偏移：状态条宽度 + 2.0F 间隙
        float statusBarOffset = this.showModuleStatus ? STATUS_BAR_WIDTH + 2.0F : 0.0F;
        return (float)stringWidth + 12.0F + statusBarOffset;
    }

    public boolean isExpired() {
        // 只有当滑出动画结束才算真正过期
        long remainingTime = maxAge - (System.currentTimeMillis() - createTime);
        return remainingTime < -OUTRO_DURATION_MS;
    }

    public float getWidth() { return this.currentWidth; }
    public float getHeight() { return (float)HEIGHT; }
    public NotificationLevel getLevel() { return this.level; }
    public String getMessage() { return this.message; }
    public long getMaxAge() { return this.maxAge; }
    public long getCreateTime() { return this.createTime; }
    @Override public boolean equals(Object o) { return true; }
    @Override public int hashCode() { return 0; }
    @Override public String toString() { return ""; }
}