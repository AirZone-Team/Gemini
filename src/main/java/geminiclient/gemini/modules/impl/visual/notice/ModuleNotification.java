package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import java.awt.Color;

/**
 * 最终版本：
 * 1. 从右侧滑入/滑出动画。
 * 2. 整个背景作为进度条。
 * 3. 可选的左侧模块状态指示条 (绿色/红色)。
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
    private static final int STATIC_BG_COLOR = 0xAA000000; // 进度条收缩后露出的静态背景颜色
    private static final int BORDER_COLOR = 0xFF000000; // 黑色边框
    private static final int TEXT_COLOR = Color.WHITE.getRGB(); // 白色文本
    private static final int STATUS_ENABLED_COLOR = 0xFF00FF00; // 绿色
    private static final int STATUS_DISABLED_COLOR = 0xFFFF0000; // 红色
    private static final int STATUS_BAR_WIDTH = 3; // 状态条宽度 (新的常量)


    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();

    // --- 新增属性 ---
    private final boolean showModuleStatus; // 是否显示状态条
    private final boolean moduleIsEnabled;  // 模块的当前状态
    // ----------------


    private float currentWidth;

    /**
     * 完整构造函数。
     * @param level 通知等级
     * @param message 消息
     * @param age 持续时间 (ms)
     * @param isEnabled 模块当前状态 (true=开启/绿色, false=关闭/红色)
     * @param showStatus 是否在左侧绘制状态条
     */
    public ModuleNotification(NotificationLevel level, String message, long age, boolean isEnabled, boolean showStatus) {
        this.level = level;
        this.message = message;
        this.maxAge = Math.max(age, (long)INTRO_DURATION_MS + OUTRO_DURATION_MS);
        this.moduleIsEnabled = isEnabled;
        this.showModuleStatus = showStatus;
        this.currentWidth = this.calculateTargetWidth();
    }

    /**
     * 简化构造函数，不显示状态条，默认关闭。
     */
    public ModuleNotification(NotificationLevel level, String message, long age) {
        this(level, message, age, false, false);
    }

    /**
     * 简化构造函数，不显示状态条，默认持续时间。
     */
    public ModuleNotification(NotificationLevel level, String message) {
        this(level, message, DEFAULT_DURATION_MS, false, false);
    }

    // ------------------------------------------------------------------------------------------------

    public void render(GuiGraphics guiGraphics, float targetX, float targetY) {
        long timeElapsed = System.currentTimeMillis() - createTime;
        long remainingTime = maxAge - timeElapsed;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // --- 1. 计算 X 轴偏移量 (滑入/滑出动画) ---

        float currentX;
        float notificationWidth = this.calculateTargetWidth();
        float offsetDistance = notificationWidth + 5.0f;

        if (timeElapsed < INTRO_DURATION_MS) {
            float progress = (float)timeElapsed / INTRO_DURATION_MS;
            currentX = Mth.lerp(progress, targetX + offsetDistance, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            float progress = (float)remainingTime / OUTRO_DURATION_MS;
            currentX = Mth.lerp(progress, targetX + offsetDistance, targetX);
        } else {
            currentX = targetX;
        }

        this.currentWidth = notificationWidth;

        if (currentX > screenWidth) {
            return;
        }

        // --- 2. 坐标计算 ---
        // 注意：所有的 X 坐标都基于 currentX (动画位置)
        int rectX1 = (int)currentX + 2;
        int rectY1 = (int)targetY + 4;
        int rectX2 = (int)(currentX + 2 + currentWidth);
        int rectY2 = (int)(targetY + 4 + 20);

        // --- 3. 绘制静态背景 (整个矩形的容器) ---
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY2, STATIC_BG_COLOR);


        // --- 4. 绘制进度条 (作为主体颜色) ---

        float lifeProgress = (float)timeElapsed / (float)maxAge;
        float barWidth = currentWidth * (1.0f - lifeProgress);

        int barColor = level.getColorInt();

        // 绘制彩色进度条
        guiGraphics.fill(
                rectX1,
                rectY1,
                (int)(rectX1 + barWidth), // 进度条动态 X 坐标
                rectY2,
                barColor
        );


        // --- 5. 绘制状态指示条 (新增部分) ---
        if (this.showModuleStatus) {
            int statusColor = this.moduleIsEnabled ? STATUS_ENABLED_COLOR : STATUS_DISABLED_COLOR;

            // 状态条绘制在通知的最左侧，宽度为 STATUS_BAR_WIDTH (2px)
            guiGraphics.fill(
                    rectX1, // 左侧 X 坐标
                    rectY1, // 顶部 Y 坐标
                    rectX1 + STATUS_BAR_WIDTH, // 右侧 X 坐标 (宽度为 2px)
                    rectY2, // 底部 Y 坐标
                    statusColor
            );

            // 为了让进度条和文本不被状态条覆盖，我们将它们稍微向右移动
            rectX1 += STATUS_BAR_WIDTH;
        }


        // --- 6. 绘制边框 ---
        // 边框绘制在最外层
        guiGraphics.hLine((int)currentX + 2, rectX2 - 1, rectY1, BORDER_COLOR);
        guiGraphics.hLine((int)currentX + 2, rectX2 - 1, rectY2 - 1, BORDER_COLOR);
        guiGraphics.vLine((int)currentX + 2, rectY1, rectY2 - 1, BORDER_COLOR);
        guiGraphics.vLine(rectX2 - 1, rectY1, rectY2 - 1, BORDER_COLOR);


        // --- 7. 绘制文本 ---
        // 文本的 X 坐标需要额外加上状态条的宽度，以确保它不会被状态条遮挡
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                this.message,
                (int)currentX + 6 + (this.showModuleStatus ? STATUS_BAR_WIDTH : 0), // 文本 X 偏移
                (int)targetY + 9,
                TEXT_COLOR,
                true
        );
    }

    // --- Getters 和辅助方法 (保持不变) ---
    // 修复文本宽度计算，确保其与状态条逻辑兼容
    public float calculateTargetWidth() {
        int stringWidth = Minecraft.getInstance().font.width(this.message);
        // 如果显示状态条，总宽度需要加上状态条的宽度和额外的间隙 (例如 2px)
        float statusBarOffset = this.showModuleStatus ? STATUS_BAR_WIDTH + 2.0F : 0.0F;
        return (float)stringWidth + 12.0F + statusBarOffset;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > (createTime + maxAge);
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