package geminiclient.gemini.modules.impl.visual.notice;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomBlurRenderer;
import geminiclient.gemini.modules.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static geminiclient.gemini.base.MinecraftInstance.mc;

public class NotificationManager {

    private final List<ModuleNotification> notifications = new CopyOnWriteArrayList<>();
    // 增加间距，提升呼吸感
    private static final int PADDING_Y = 8;     // 原为 6[cite: 3]
    private static final int MARGIN = 12;       // 原为 10[cite: 3]
    private static final int BLUR_EXIT_DURATION_MS = 320;
    private static final float BG_PADDING_X = 6f;
    private static final float BG_PADDING_Y = 6f;

    private float lastTargetYOffset = 0f;
    private float smoothYOffset = 0f;
    private float smoothMaxWidth = 0f;

    // 模糊背景出场动画状态（启动时快照全部位置参数，防止 lerp 导致的频闪）
    private float exitBlurProgress = 0f;
    private float exitBlurStartX = 0f;
    private float exitBlurStartY = 0f;
    private float exitBlurStartWidth = 0f;
    private float exitBlurStartHeight = 0f;
    private long exitBlurStartTime = 0L;
    private boolean exitBlurActive = false;

    public void addNotification(ModuleNotification.NotificationLevel level, String message,
                                long duration) {
        notifications.add(new ModuleNotification(level, message, duration));
    }

    private float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    public void renderOutline(GuiGraphicsExtractor guiGraphics, Module module) {
        renderDummy(guiGraphics, module, 0xAAFFD700, 2);
    }

    public void renderPlaceholder(GuiGraphicsExtractor guiGraphics, Module module) {
        renderDummy(guiGraphics, module, 0x40FFFFFF, 1);
    }

    private void renderDummy(GuiGraphicsExtractor guiGraphics, Module module, int color, int bw) {
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        String dummyMessage = "Module Notification";
        float textWidth = mc.font.width(dummyMessage) + 20 + 2 * 14 + 8 + 8;
        float width = textWidth + MARGIN * 2;
        float height = 32 + PADDING_Y;

        float x = rightAligned ? originX - MARGIN - textWidth : originX;
        float y = originY - MARGIN;

        int ix = (int) x, iy = (int) y, iw = (int) width, ih = (int) height;
        CustomRectRenderer.drawRect(guiGraphics, ix, iy, iw, bw, color);
        CustomRectRenderer.drawRect(guiGraphics, ix, iy + ih - bw, iw, bw, color);
        CustomRectRenderer.drawRect(guiGraphics, ix, iy, bw, ih, color);
        CustomRectRenderer.drawRect(guiGraphics, ix + iw - bw, iy, bw, ih, color);

        Gemini.hudDragManager.registerDragRegion(module, (int) x, (int) y, (int) width, (int) height);
    }

    public void renderAll(GuiGraphicsExtractor guiGraphics, Module module) {
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        notifications.removeIf(ModuleNotification::isExpired);

        // --- 计算总高度和最大宽度（空列表时取 0） ---
        float totalHeight = 0f;
        float maxWidth = 0f;
        for (ModuleNotification n : notifications) {
            totalHeight += n.getHeight() + PADDING_Y;
            float w = n.calculateTargetWidth();
            if (w > maxWidth) maxWidth = w;
        }

        float targetY = originY - MARGIN - totalHeight + PADDING_Y;
        if (lastTargetYOffset == 0f) lastTargetYOffset = targetY;

        // 只在有通知时更新平滑值
        if (!notifications.isEmpty()) {
            // Y轴平滑
            smoothYOffset = Mth.lerp(0.12f, smoothYOffset, targetY);
            lastTargetYOffset = targetY;

            // X轴（宽度）平滑过渡
            if (maxWidth > smoothMaxWidth) {
                smoothMaxWidth = Mth.lerp(0.15f, smoothMaxWidth, maxWidth);
            } else {
                smoothMaxWidth = Mth.lerp(0.08f, smoothMaxWidth, maxWidth);
            }
        }

        float currentYOffset = smoothYOffset;

        // --- 计算模糊矩形位置（仅用于启动快照或常规渲染） ---
        float blurX = rightAligned
                ? originX - MARGIN - smoothMaxWidth - BG_PADDING_X
                : originX + MARGIN - BG_PADDING_X;

        float blurY = smoothYOffset - BG_PADDING_Y;
        float blurWidth = smoothMaxWidth + BG_PADDING_X * 2;
        float blurHeight = totalHeight - PADDING_Y + BG_PADDING_Y * 2;

        // --- 检查退场状态 ---
        boolean allOutro = !notifications.isEmpty();
        for (ModuleNotification n : notifications) {
            if (!n.isInOutro()) {
                allOutro = false;
                break;
            }
        }

        // 有新通知到来 → 取消背景出场动画（仅在列表非空时判断）
        if (exitBlurActive && !notifications.isEmpty() && !allOutro) {
            exitBlurActive = false;
            exitBlurProgress = 0f;
        }

        // 全部进入退场 → 快照当前模糊位置与尺寸，触发背景出场动画
        if (allOutro && !exitBlurActive) {
            exitBlurActive = true;
            exitBlurStartTime = System.currentTimeMillis();
            exitBlurProgress = 0f;
            // 【修复】快照全部位置参数，避免 smoothYOffset/smoothMaxWidth 持续 lerp 导致频闪
            exitBlurStartX = blurX;
            exitBlurStartY = blurY;
            exitBlurStartWidth = blurWidth;
            exitBlurStartHeight = blurHeight;
        }

        // --- 渲染模糊背景（退场使用快照值，常规使用实时值） ---
        if (exitBlurActive) {
            long elapsed = System.currentTimeMillis() - exitBlurStartTime;
            exitBlurProgress = Math.min(1.0f, (float) elapsed / BLUR_EXIT_DURATION_MS);
            float animFactor = easeOutCubic(exitBlurProgress);
            // 【修复】使用快照坐标，消除 lerp 导致的逐帧位移
            float currentBlurHeight = exitBlurStartHeight * (1.0f - animFactor);
            // 上边缘随收缩比例下移，下边缘保持不动（收缩方向：从上往下）
            float currentBlurY = exitBlurStartY + (exitBlurStartHeight - currentBlurHeight);

            // alpha 同步衰减：0xD9 → 0x00
            int baseAlpha = 0xD9;
            int currentAlpha = Math.max(0, Math.min(255,
                    (int) (baseAlpha * (1.0f - animFactor))));
            int currentTint = (currentAlpha << 24) | 0x141414;

            if (currentBlurHeight > 0.5f && currentAlpha > 0) {
                CustomBlurRenderer.render(exitBlurStartX, currentBlurY,
                        exitBlurStartWidth, currentBlurHeight,
                        4f, currentTint, 12f);
            }

            if (exitBlurProgress >= 1.0f) {
                exitBlurActive = false;
                exitBlurProgress = 0f;
            }
        } else if (!notifications.isEmpty()) {
            // 常规渲染：只有列表非空时才绘制模糊
            CustomBlurRenderer.render(blurX, blurY, blurWidth, blurHeight,
                    4f, 0xD9141414, 12f);
        }

        // 【修复】空列表时不再立即掐断退场动画，而是让动画自然完结
        if (notifications.isEmpty()) {
            if (!exitBlurActive) {
                smoothYOffset = originY - MARGIN;
                lastTargetYOffset = smoothYOffset;
            }
            return;
        }

        // --- 绘制每个通知的内容（圆形图标 + 文本） ---
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ModuleNotification notification = notifications.get(i);
            float notificationWidth = notification.calculateTargetWidth();
            float notificationHeight = notification.getHeight();

            float targetX = rightAligned
                    ? originX - MARGIN - notificationWidth
                    : originX + MARGIN;

            notification.render(guiGraphics, targetX, currentYOffset);

            currentYOffset += notificationHeight + PADDING_Y;
        }

        // Register drag region
        float totalH = originY - smoothYOffset;
        if (totalH > 0 && maxWidth > 0) {
            float regionW = maxWidth + MARGIN * 2;
            float regionX = rightAligned ? originX - MARGIN - maxWidth : originX;
            Gemini.hudDragManager.registerDragRegion(module, (int) regionX, (int) smoothYOffset, (int) regionW, (int) totalH);
        }
    }
}