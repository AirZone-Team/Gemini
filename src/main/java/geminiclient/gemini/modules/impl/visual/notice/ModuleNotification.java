package geminiclient.gemini.modules.impl.visual.notice;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class ModuleNotification {

    public enum NotificationLevel {
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

        public int getGlowColorInt() {
            return 0x80000000 | (this.color & 0xFFFFFF); // 半透明发光色
        }

        public String getIcon() {
            return icon;
        }
    }

    // --- 尺寸与排版优化 ---
    private static final int HEIGHT = 32;                     // 稍微增高以容纳更宽裕的排版
    private static final int PROGRESS_BAR_HEIGHT = 2;         // 进度条稍微加粗
    private static final int STATUS_BAR_WIDTH = 3;            // 状态指示条加宽
    private static final int CORNER_RADIUS = 6;               // 圆角加大，更柔和
    private static final int PADDING_X = 14;                  // 增加水平留白
    private static final int PADDING_Y = 6;

    private static final int INTRO_DURATION_MS = 400;         // 配合回弹动画，时间稍拉长
    private static final int OUTRO_DURATION_MS = 350;
    private static final int MIN_LIFE_MS = INTRO_DURATION_MS + OUTRO_DURATION_MS;

    // --- 调色板 ---
    private static final int BG_COLOR_BASE = 0x141414;        // 提升一点点明度，避免死黑
    private static final int SHADOW_COLOR_BASE = 0x000000;
    private static final int BORDER_COLOR_BASE = 0xFFFFFF;
    private static final int TEXT_COLOR_BASE = 0xFAFAFA;      // 更纯净的白
    private static final int STATUS_ENABLED_COLOR = 0x43E096;
    private static final int STATUS_DISABLED_COLOR = 0xFF5B5B;

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

    // 【新增】带回弹效果的进场缓动 (Ease Out Back)
    private float easeOutBack(float x) {
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    // 退场依然使用平滑的 Cubic
    private float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private float getAlphaFactor(long timeElapsed, long remainingTime) {
        // 让透明度渐变比位移快一点，显得更自然
        if (timeElapsed < INTRO_DURATION_MS - 100) {
            return Math.min(1.0f, (float) timeElapsed / (INTRO_DURATION_MS - 100));
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            return Math.max(0.0f, (float) remainingTime / OUTRO_DURATION_MS);
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
        float initialOffset = notificationWidth + 40f;

        float currentX;
        if (timeElapsed < INTRO_DURATION_MS) {
            float progress = (float) timeElapsed / INTRO_DURATION_MS;
            // 进场使用回弹效果
            currentX = Mth.lerp(easeOutBack(progress), targetX + initialOffset, targetX);
        } else if (remainingTime <= OUTRO_DURATION_MS) {
            float progress = 1.0F - (float) remainingTime / OUTRO_DURATION_MS;
            // 退场平滑滑出
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

        int rectWidth = rectX2 - rectX1;
        int rectHeight = rectY2 - rectY1;

        // --- 1. 阴影渲染 ---
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics,
                rectX1 + 2, rectY1 + 3, rectWidth, rectHeight, CORNER_RADIUS,
                applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.25f));
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics,
                rectX1 + 4, rectY1 + 6, rectWidth, rectHeight, CORNER_RADIUS + 2,
                applyAlpha(SHADOW_COLOR_BASE, alphaFactor * 0.10f));

        // --- 2. 主背景与边框 ---
        int bgColor = applyAlpha(BG_COLOR_BASE, alphaFactor * 0.85f);
        // 边框高光仅在顶部或四周微弱存在
        int borderColor = applyAlpha(BORDER_COLOR_BASE, alphaFactor * 0.08f);
        CustomRoundedRectRenderer.drawRoundedBorderedRect(guiGraphics,
                rectX1, rectY1, rectWidth, rectHeight, CORNER_RADIUS,
                bgColor, borderColor, 1);

        int textXOffset = PADDING_X;

        // --- 3. 状态胶囊 (Pill Status Bar) ---
        if (showModuleStatus) {
            int statusColor = applyAlpha(moduleIsEnabled ? STATUS_ENABLED_COLOR : STATUS_DISABLED_COLOR, alphaFactor);
            // 绘制成悬浮在内部的小圆角矩形/胶囊
            CustomRoundedRectRenderer.drawRoundedRect(guiGraphics,
                    rectX1 + 6,
                    rectY1 + (rectHeight / 2) - 5,
                    STATUS_BAR_WIDTH,
                    10,
                    1, // 小圆角
                    statusColor
            );
            textXOffset = STATUS_BAR_WIDTH + PADDING_X + 4; // 根据悬浮胶囊的位置调整文本偏移
        }

        // --- 4. 文本与图标渲染 ---
        String icon = level.getIcon();
        Minecraft mc = Minecraft.getInstance();
        int iconColor = applyAlpha(level.getColorInt(), alphaFactor);

        int contentHeight = mc.font.lineHeight;
        int iconY = rectY1 + (rectY2 - rectY1 - contentHeight) / 2;

        // 绘制图标发光底色 (如果渲染器支持)
        CustomRoundedRectRenderer.drawRoundedRect(guiGraphics,
                rectX1 + textXOffset - 2, iconY - 2, mc.font.width(icon) + 4, contentHeight + 4, 4,
                applyAlpha(level.getGlowColorInt(), alphaFactor * 0.15f));

        guiGraphics.text(mc.font, icon, rectX1 + textXOffset, iconY, iconColor, false);

        int iconXOffset = mc.font.width(icon) + 8; // 图标与文字的舒朗间距

        int textColor = applyAlpha(TEXT_COLOR_BASE, alphaFactor);
        guiGraphics.text(mc.font, message, rectX1 + textXOffset + iconXOffset, iconY, textColor, false);

        // --- 5. 圆角进度条 ---
        float progressRatio = Mth.clamp((float) remainingTime / (float) (maxAge - MIN_LIFE_MS), 0.0F, 1.0F);
        // 让进度条两侧保留一点内边距，看起来更精致
        int barPaddingX = 4;
        float progressBarFullWidth = rectWidth - (barPaddingX * 2);
        float progressWidth = progressBarFullWidth * progressRatio;

        int barX1 = rectX1 + barPaddingX;
        int barY = rectY2 - PROGRESS_BAR_HEIGHT - 2; // 底部留 2 像素间隙

        int progressColor = applyAlpha(level.getColorInt(), alphaFactor * 0.9f);

        // 使用圆角矩形绘制进度条，代替生硬的直角填充
        if (progressWidth > 1) {
            CustomRoundedRectRenderer.drawRoundedRect(guiGraphics,
                    barX1, barY, (int)progressWidth, PROGRESS_BAR_HEIGHT, PROGRESS_BAR_HEIGHT / 2, progressColor);
        }
    }

    public float calculateTargetWidth() {
        String icon = level.getIcon();
        int iconWidth = Minecraft.getInstance().font.width(icon);
        int stringWidth = Minecraft.getInstance().font.width(message);
        float textTotalWidth = stringWidth + iconWidth + 2.0F * PADDING_X + 8; // 图标间距增加
        float statusBarOffset = showModuleStatus ? (STATUS_BAR_WIDTH + 8) : 0.0F; // 胶囊状态栏的占用宽度
        return textTotalWidth + statusBarOffset;
    }

    public boolean isExpired() {
        long remainingTime = maxAge - (System.currentTimeMillis() - createTime);
        return remainingTime < -OUTRO_DURATION_MS;
    }

    public float getWidth() { return currentWidth; }
    public float getHeight() { return (float) HEIGHT; }
}