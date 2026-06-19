package geminiclient.gemini.modules.impl.visual.notice;

import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ModuleNotification {

    public enum NotificationLevel {
        INFO(0x22C55E, Identifier.fromNamespaceAndPath("gemini", "icon/notice/check.png")),
        WARN(0xF59E0B, Identifier.fromNamespaceAndPath("gemini", "icon/notice/priority_high.png")),
        ERROR(0xEF4444, Identifier.fromNamespaceAndPath("gemini", "icon/notice/close.png"));

        private final int color;
        private final Identifier texture;

        NotificationLevel(int color, Identifier texture) {
            this.color = color;
            this.texture = texture;
        }

        public int getColorInt() {
            return 0xFF000000 | this.color;
        }

        public Identifier getTexture() {
            return texture;
        }
    }

    // --- 尺寸与排版优化 ---
    private static final int HEIGHT = 32;
    private static final int PADDING_X = 14;
    private static final int PADDING_Y = 6;
    private static final int CIRCLE_SIZE = 16;      // 圆形背景直径
    private static final int ICON_SIZE = 12;         // 图标渲染尺寸
    private static final int ICON_TEX_SIZE = 24;     // PNG 原始尺寸

    private static final int INTRO_DURATION_MS = 400;
    private static final int OUTRO_DURATION_MS = 350;
    private static final int MIN_LIFE_MS = INTRO_DURATION_MS + OUTRO_DURATION_MS;

    // --- 调色板 ---
    private static final int TEXT_COLOR_BASE = 0xFAFAFA;

    private final NotificationLevel level;
    private final String message;
    private final long maxAge;
    private final long createTime = System.currentTimeMillis();

    private float currentWidth;
    private float renderXOffset = 0f;

    public ModuleNotification(NotificationLevel level, String message, long age) {
        this.level = level;
        this.message = message;
        this.maxAge = Math.max(age, MIN_LIFE_MS);
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
        int rectY2 = (int) (targetY + PADDING_Y + (HEIGHT - PADDING_Y * 2));

        // 背景已由 NotificationManager 统一绘制

        // --- 图标圆形背景 + PNG 图标 ---
        Identifier texture = level.getTexture();
        int circleColor = applyAlpha(level.getColorInt(), alphaFactor);

        int circleX = rectX1 + PADDING_X;
        int circleY = rectY1 + (rectY2 - rectY1 - CIRCLE_SIZE) / 2;

        // 绘制圆形背景
        CustomRoundedRectRenderer.drawRoundedRect(
                guiGraphics, circleX, circleY, CIRCLE_SIZE, CIRCLE_SIZE,
                CIRCLE_SIZE / 2, circleColor);

        // 在圆形上绘制 PNG 图标
        int iconX = circleX + (CIRCLE_SIZE - ICON_SIZE) / 2;
        int iconY = circleY + (CIRCLE_SIZE - ICON_SIZE) / 2;
        int iconTint = applyAlpha(0xFFFFFF, alphaFactor);
// 获取矩阵栈 (具体方法取决于你的 GuiGraphicsExtractor 实现，通常是 .pose() 或原生 GuiGraphics)
        var pose = guiGraphics.pose();
        pose.pushMatrix();

// 1. 将原点平移到目标坐标
        pose.translate(iconX, iconY);

// 2. 计算缩放比例 (12 / 24 = 0.5)
        float scale = (float) ICON_SIZE / ICON_TEX_SIZE;
        pose.scale(scale, scale);

// 3. 此时坐标和缩放已经应用在矩阵上，所以坐标传 0，并使用 24x24 (ICON_TEX_SIZE) 绘制完整纹理
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                0, 0, 0, 0, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, iconTint);

        pose.popMatrix();

        // --- 3. 消息文本 ---
        Minecraft mc = Minecraft.getInstance();
        int contentHeight = mc.font.lineHeight;
        int textY = rectY1 + (rectY2 - rectY1 - contentHeight) / 2;
        int textColor = applyAlpha(TEXT_COLOR_BASE, alphaFactor);
        guiGraphics.text(mc.font, message, rectX1 + PADDING_X + CIRCLE_SIZE + 8, textY, textColor, false);
    }

    public float calculateTargetWidth() {
        int stringWidth = Minecraft.getInstance().font.width(message);
        return stringWidth + CIRCLE_SIZE + 2.0F * PADDING_X + 8;
    }

    public boolean isInOutro() {
        long timeElapsed = System.currentTimeMillis() - createTime;
        return timeElapsed >= INTRO_DURATION_MS
                && (maxAge - timeElapsed) <= OUTRO_DURATION_MS;
    }

    public boolean isExpired() {
        long remainingTime = maxAge - (System.currentTimeMillis() - createTime);
        return remainingTime < -OUTRO_DURATION_MS;
    }

    public float getWidth() { return currentWidth; }
    public float getHeight() { return (float) HEIGHT; }
}