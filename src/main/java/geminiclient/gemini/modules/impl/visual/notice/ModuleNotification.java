package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import java.awt.Color;

/**
 * 兼容1.21.11 NeoForge的美化通知组件：
 * - 深灰色半透明背景 + 细边框（纯矩形）
 * - 阴影效果（通过偏移矩形实现）
 * - 状态指示条（绿/红）
 * - 底部彩色进度条（信息/警告/错误）
 * - 平滑滑入滑出动画 + 透明度淡入淡出
 * - 级别图标（i / ! / ✕）
 * - 全部使用 GuiGraphics.fill 绘制，无任何 RenderSystem 依赖
 */
public class ModuleNotification {

    public enum NotificationLevel {
        INFO(0x4A90E2, "i"),    // 柔和蓝色
        WARN(0xFF9800, "!"),    // 橙色
        ERROR(0xE54B4B, "✕");   // 柔红

        private final int color;
        private final String icon;

        NotificationLevel(int color, String icon) {
            this.color = color;
            this.icon = icon;
        }

        public int getColorInt() {
            return 0xFF000000 | this.color;
        }

        public String getIcon() {
            return icon;
        }
    }

    // 尺寸常量
    private static final int HEIGHT = 28;                      // 整体高度（含内边距）
    private static final int PROGRESS_BAR_HEIGHT = 2;          // 底部进度条高度
    private static final int STATUS_BAR_WIDTH = 3;             // 状态条宽度
    private static final int ICON_SIZE = 10;                    // 图标区域大小（暂未使用）
    private static final int PADDING_X = 8;                     // 文本左右内边距
    private static final int PADDING_Y = 4;                     // 上下内边距

    // 动画时长
    private static final int INTRO_DURATION_MS = 500;
    private static final int OUTRO_DURATION_MS = 500;
    private static final int MIN_LIFE_MS = INTRO_DURATION_MS + OUTRO_DURATION_MS;

    // 颜色常量（基础颜色，无透明度）
    private static final int BG_COLOR_BASE = 0x1E1E1E;           // 深灰色
    private static final int SHADOW_COLOR_BASE = 0x000000;       // 阴影黑色
    private static final int BORDER_COLOR_BASE = 0xFFFFFF;       // 边框白色
    private static final int TEXT_COLOR_BASE = 0xFFFFFF;         // 文字白色
    private static final int STATUS_ENABLED_COLOR = 0x4CAF50;    // 绿色
    private static final int STATUS_DISABLED_COLOR = 0xF44336;   // 红色

    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();
    private final boolean showModuleStatus;
    private final boolean moduleIsEnabled;

    private float currentWidth; // 用于外部获取宽度（未使用）

    public ModuleNotification(NotificationLevel level, String message, long age, boolean isEnabled, boolean showStatus) {
        this.level = level;
        this.message = message;
        this.maxAge = Math.max(age, MIN_LIFE_MS);
        this.moduleIsEnabled = isEnabled;
        this.showModuleStatus = showStatus;
        this.currentWidth = calculateTargetWidth();
    }

    private float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3);
    }

    /**
     * 根据动画进度计算当前的透明度因子 (0~1)
     */
    private float getAlphaFactor(long timeElapsed, long remainingTime) {
        if (timeElapsed < INTRO_DURATION_MS) {
            return (float) timeElapsed / INTRO_DURATION_MS;
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            return (float) remainingTime / OUTRO_DURATION_MS;
        } else {
            return 1.0F;
        }
    }

    /**
     * 将基础颜色与透明度因子合成最终 ARGB 颜色
     */
    private int applyAlpha(int baseRgb, float alphaFactor) {
        int alpha = Math.min(255, Math.max(0, (int) (alphaFactor * 255)));
        return (alpha << 24) | (baseRgb & 0xFFFFFF);
    }

    public void render(GuiGraphics guiGraphics, float targetX, float targetY) {
        long timeElapsed = System.currentTimeMillis() - createTime;
        long remainingTime = maxAge - timeElapsed;

        if (remainingTime < -OUTRO_DURATION_MS) return;

        float notificationWidth = calculateTargetWidth();
        float initialOffset = notificationWidth + 10.0f;

        float currentX;
        if (timeElapsed < INTRO_DURATION_MS) {
            float progress = (float) timeElapsed / INTRO_DURATION_MS;
            currentX = Mth.lerp(easeOutCubic(progress), targetX + initialOffset, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            float progress = 1.0F - (float) remainingTime / OUTRO_DURATION_MS;
            currentX = Mth.lerp(easeOutCubic(progress), targetX, targetX + initialOffset);
        } else {
            currentX = targetX;
        }

        this.currentWidth = notificationWidth;

        float alphaFactor = getAlphaFactor(timeElapsed, remainingTime);

        int rectX1 = (int) currentX;
        int rectY1 = (int) targetY + PADDING_Y;
        int rectX2 = (int) (currentX + notificationWidth);
        int rectY2 = (int) (targetY + PADDING_Y + (HEIGHT - PADDING_Y * 2));

        // 绘制阴影（比背景略大，偏移2像素，半透明）
        int shadowColor = applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.5f);
        guiGraphics.fill(rectX1 + 2, rectY1 + 2, rectX2 + 2, rectY2 + 2, shadowColor);

        // 绘制背景（80%透明度）
        int bgColor = applyAlpha(BG_COLOR_BASE, alphaFactor * 0.8f);
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY2, bgColor);

        // 绘制边框（半透明，四条边）
        int borderColor = applyAlpha(BORDER_COLOR_BASE, alphaFactor * 0.2f);
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY1 + 1, borderColor); // 上
        guiGraphics.fill(rectX1, rectY2 - 1, rectX2, rectY2, borderColor); // 下
        guiGraphics.fill(rectX1, rectY1, rectX1 + 1, rectY2, borderColor); // 左
        guiGraphics.fill(rectX2 - 1, rectY1, rectX2, rectY2, borderColor); // 右

        int textXOffset = PADDING_X;
        int iconXOffset = 0;

        // 绘制状态指示条（左侧）
        if (showModuleStatus) {
            int statusColor = applyAlpha(moduleIsEnabled ? STATUS_ENABLED_COLOR : STATUS_DISABLED_COLOR, alphaFactor);
            guiGraphics.fill(
                    rectX1,
                    rectY1,
                    rectX1 + STATUS_BAR_WIDTH,
                    rectY2 - PROGRESS_BAR_HEIGHT,
                    statusColor
            );
            textXOffset = STATUS_BAR_WIDTH + PADDING_X;
        }

        // 绘制级别图标（在文本左侧）
        String icon = level.getIcon();
        int iconWidth = Minecraft.getInstance().font.width(icon);
        int iconColor = applyAlpha(level.getColorInt(), alphaFactor);
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                icon,
                rectX1 + textXOffset,
                rectY1 + (HEIGHT - PADDING_Y * 2 - Minecraft.getInstance().font.lineHeight) / 2,
                iconColor,
                false // 图标不启用阴影，避免重叠
        );
        iconXOffset = iconWidth + 4; // 图标与文本间距

        // 绘制文本（带阴影）
        int textColor = applyAlpha(TEXT_COLOR_BASE, alphaFactor);
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                message,
                rectX1 + textXOffset + iconXOffset,
                rectY1 + (HEIGHT - PADDING_Y * 2 - Minecraft.getInstance().font.lineHeight) / 2,
                textColor,
                true
        );

        // 底部进度条 - 修正版本
        float progressRatio = Mth.clamp((float) remainingTime / (float) (maxAge - MIN_LIFE_MS), 0.0F, 1.0F);
        float progressBarFullWidth = notificationWidth - (showModuleStatus ? STATUS_BAR_WIDTH : 0); // 只减去状态条宽度
        float progressWidth = progressBarFullWidth * progressRatio;

        int barX1 = rectX1 + (showModuleStatus ? STATUS_BAR_WIDTH : 0); // 从状态条右侧开始
        int progressColor = applyAlpha(level.getColorInt(), alphaFactor);
        guiGraphics.fill(
                barX1,
                rectY2 - PROGRESS_BAR_HEIGHT,
                (int) (barX1 + progressWidth),
                rectY2,
                progressColor
        );
    }

    public float calculateTargetWidth() {
        String icon = level.getIcon();
        int iconWidth = Minecraft.getInstance().font.width(icon);
        int stringWidth = Minecraft.getInstance().font.width(message);
        float textTotalWidth = stringWidth + iconWidth + 2.0F * PADDING_X + 4; // 4为图标与文本间距
        float statusBarOffset = showModuleStatus ? STATUS_BAR_WIDTH : 0.0F;
        return textTotalWidth + statusBarOffset;
    }

    public boolean isExpired() {
        long remainingTime = maxAge - (System.currentTimeMillis() - createTime);
        return remainingTime < -OUTRO_DURATION_MS;
    }

    public float getWidth() { return currentWidth; }
    public float getHeight() { return (float) HEIGHT; }
}