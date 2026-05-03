package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class ModuleNotification {

    public enum NotificationLevel {
        // 使用现代 UI 常用的柔和/低饱和高明度色彩[cite: 4]
        INFO(0x3B82F6, "i"),    // 现代蓝
        WARN(0xF59E0B, "!"),    // 琥珀橙
        ERROR(0xEF4444, "✕");   // 柔和红

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

    // 尺寸与排版优化：更修长、更通透[cite: 4]
    private static final int HEIGHT = 28;
    private static final int PROGRESS_BAR_HEIGHT = 1;         // 进度条改为极细的 1px，更显精致
    private static final int STATUS_BAR_WIDTH = 2;            // 统一为 2px（与 Arraylists 一致）
    private static final int PADDING_X = 12;                  // 增加水平内边距
    private static final int PADDING_Y = 4;

    private static final int INTRO_DURATION_MS = 350;         // 进场更清脆
    private static final int OUTRO_DURATION_MS = 400;
    private static final int MIN_LIFE_MS = INTRO_DURATION_MS + OUTRO_DURATION_MS;

    // 与 Arraylists 统一的暗黑调色板[cite: 4]
    private static final int BG_COLOR_BASE = 0x0F0F0F;        // 极深灰（近纯黑）
    private static final int SHADOW_COLOR_BASE = 0x000000;
    private static final int BORDER_COLOR_BASE = 0xFFFFFF;    // 用作高光计算
    private static final int TEXT_COLOR_BASE = 0xF5F5F5;      // 柔和白
    private static final int STATUS_ENABLED_COLOR = 0x43E096; // 现代翠绿
    private static final int STATUS_DISABLED_COLOR = 0xFF5B5B; // 柔和珊瑚红

    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();
    private final boolean showModuleStatus;
    private final boolean moduleIsEnabled;

    private float currentWidth;
    private float renderXOffset = 0f;

    public ModuleNotification(NotificationLevel level, String message, long age, boolean isEnabled, boolean showStatus) {
        this.level = level;
        this.message = message;
        this.maxAge = Math.max(age, MIN_LIFE_MS);
        this.moduleIsEnabled = isEnabled;
        this.showModuleStatus = showStatus;
        this.currentWidth = calculateTargetWidth();
    }

    public void setRenderXOffset(float offset) {
        this.renderXOffset = offset;
    }

    private float easeOutCubic(float x) {
        return 1.0F - (float) Math.pow(1.0F - x, 3);
    }

    private float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private float getAlphaFactor(long timeElapsed, long remainingTime) {
        if (timeElapsed < INTRO_DURATION_MS) {
            return easeOutCubic((float) timeElapsed / INTRO_DURATION_MS);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            return easeOutCubic((float) remainingTime / OUTRO_DURATION_MS);
        } else {
            return 1.0F;
        }
    }

    private int applyAlpha(int baseRgb, float alphaFactor) {
        int alpha = Math.min(255, Math.max(0, (int) (alphaFactor * 255)));
        return (alpha << 24) | (baseRgb & 0xFFFFFF);
    }

    public void render(GuiGraphicsExtractor guiGraphics, float targetX, float targetY) {
        long timeElapsed = System.currentTimeMillis() - createTime;
        long remainingTime = maxAge - timeElapsed;

        if (remainingTime < -OUTRO_DURATION_MS) return;

        float notificationWidth = calculateTargetWidth();
        float initialOffset = notificationWidth + 30f; // 增加入场滑动的距离感[cite: 4]

        float currentX;
        if (timeElapsed < INTRO_DURATION_MS) {
            float progress = (float) timeElapsed / INTRO_DURATION_MS;
            currentX = Mth.lerp(easeOutCubic(progress), targetX + initialOffset, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            float progress = 1.0F - (float) remainingTime / OUTRO_DURATION_MS;
            currentX = Mth.lerp(easeInOutCubic(progress), targetX, targetX + initialOffset);
        } else {
            currentX = targetX;
        }

        this.currentWidth = notificationWidth;
        currentX += renderXOffset;

        float alphaFactor = getAlphaFactor(timeElapsed, remainingTime);

        int rectX1 = (int) currentX;
        int rectY1 = (int) targetY + PADDING_Y;
        int rectX2 = (int) (currentX + notificationWidth);
        int rectY2 = (int) (targetY + PADDING_Y + (HEIGHT - PADDING_Y * 2));

        // 优化：更深邃柔和的三层阴影[cite: 4]
        guiGraphics.fill(rectX1 + 1, rectY1 + 1, rectX2 + 1, rectY2 + 1, applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.20f));
        guiGraphics.fill(rectX1 + 2, rectY1 + 3, rectX2 + 2, rectY2 + 3, applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.10f));
        guiGraphics.fill(rectX1 + 3, rectY1 + 5, rectX2 + 3, rectY2 + 5, applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.05f));

        // 背景主体（75%透明度）
        int bgColor = applyAlpha(BG_COLOR_BASE, alphaFactor * 0.75f);
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY2, bgColor);

        // 顶部精细高光线，提升质感
        int highlightColor = applyAlpha(BORDER_COLOR_BASE, alphaFactor * 0.12f);
        guiGraphics.fill(rectX1, rectY1, rectX2, rectY1 + 1, highlightColor);

        // 微弱的边框描边
        int borderColor = applyAlpha(BORDER_COLOR_BASE, alphaFactor * 0.08f);
        guiGraphics.fill(rectX1, rectY2 - 1, rectX2, rectY2, borderColor); // 下
        guiGraphics.fill(rectX1, rectY1, rectX1 + 1, rectY2, borderColor); // 左
        guiGraphics.fill(rectX2 - 1, rectY1, rectX2, rectY2, borderColor); // 右

        int textXOffset = PADDING_X;
        int iconXOffset = 0;

        // 状态指示条
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

        String icon = level.getIcon();
        Minecraft mc = Minecraft.getInstance();
        int iconWidth = mc.font.width(icon);
        int iconColor = applyAlpha(level.getColorInt(), alphaFactor);

        // 确保图标和文本绝对居中
        int contentHeight = mc.font.lineHeight;
        int iconY = rectY1 + (rectY2 - rectY1 - contentHeight) / 2;

        guiGraphics.text(mc.font, icon, rectX1 + textXOffset + 1, iconY + 1, applyAlpha(0x000000, alphaFactor * 0.4f), false);
        guiGraphics.text(mc.font, icon, rectX1 + textXOffset, iconY, iconColor, false);

        iconXOffset = iconWidth + 6; // 稍微增加图标与文本的间距

        // 文本渲染
        int textColor = applyAlpha(TEXT_COLOR_BASE, alphaFactor);
        int textShadow = applyAlpha(0x000000, alphaFactor * 0.5f);
        guiGraphics.text(mc.font, message, rectX1 + textXOffset + iconXOffset + 1, iconY + 1, textShadow, false);
        guiGraphics.text(mc.font, message, rectX1 + textXOffset + iconXOffset, iconY, textColor, false);

        // 进度条（极细样式）
        float progressRatio = Mth.clamp((float) remainingTime / (float) (maxAge - MIN_LIFE_MS), 0.0F, 1.0F);
        float progressBarFullWidth = notificationWidth - (showModuleStatus ? STATUS_BAR_WIDTH : 0);
        float progressWidth = progressBarFullWidth * progressRatio;

        int barX1 = rectX1 + (showModuleStatus ? STATUS_BAR_WIDTH : 0);
        int barY = rectY2 - PROGRESS_BAR_HEIGHT;

        // 进度条颜色稍亮，形成呼吸感[cite: 4]
        int progressColor = applyAlpha(level.getColorInt(), alphaFactor * 0.9f);
        guiGraphics.fill(barX1, barY, (int) (barX1 + progressWidth), rectY2, progressColor);
    }

    public float calculateTargetWidth() {
        String icon = level.getIcon();
        int iconWidth = Minecraft.getInstance().font.width(icon);
        int stringWidth = Minecraft.getInstance().font.width(message);
        float textTotalWidth = stringWidth + iconWidth + 2.0F * PADDING_X + 6; // 图标间距调整为 6
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