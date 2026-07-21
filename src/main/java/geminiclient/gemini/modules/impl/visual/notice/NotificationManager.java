package geminiclient.gemini.modules.impl.visual.notice;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomAcrylicRenderer;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.impl.visual.Notification;
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
    // 队列上限：超出时丢弃最旧的通知，避免刷屏时无界堆积
    private static final int MAX_NOTIFICATIONS = 8;

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
        addNotification(level, null, message, duration);
    }

    /**
     * 带标题的入队重载：Mellow 卡片显示标题行，Classic 样式忽略标题。
     * 队列满时移除最旧的一条，保证渲染开销有上界。
     */
    public void addNotification(ModuleNotification.NotificationLevel level, String title,
                                String message, long duration) {
        while (notifications.size() >= MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }
        notifications.add(new ModuleNotification(level, title, message, duration));
    }

    /** 清空队列（模式切换 / 配置重载时使用）。 */
    public void clear() {
        notifications.clear();
        exitBlurActive = false;
        exitBlurProgress = 0f;
    }

    private float easeOutCubic(float x) {
        return 1 - (float) Math.pow(1 - x, 3);
    }

    public void renderOutline(GuiGraphicsExtractor guiGraphics, Module module) {
        renderDummy(guiGraphics, module, 0xAAFFD700, 2);
    }

    private void renderDummy(GuiGraphicsExtractor guiGraphics, Module module, int color, int bw) {
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        float contentWidth;
        float height;
        if (module instanceof Notification notif && notif.isMellow()) {
            // Mellow 卡片占位尺寸：与真实卡片布局一致
            contentWidth = MellowNotificationRenderer.cardWidth("Module", "Enabled: Notification");
            height = MellowNotificationRenderer.CARD_HEIGHT + PADDING_Y;
        } else {
            String dummyMessage = "Module Notification";
            contentWidth = mc.font.width(dummyMessage) + 20 + 2 * 14 + 8 + 8;
            height = 32 + PADDING_Y;
        }

        float width = contentWidth + MARGIN * 2;
        float x = rightAligned ? originX - MARGIN - contentWidth : originX;
        float y = originY - MARGIN;

        int ix = (int) x, iy = (int) y, iw = (int) width, ih = (int) height;
        CustomRectRenderer.drawRect(guiGraphics, ix, iy, iw, bw, color);
        CustomRectRenderer.drawRect(guiGraphics, ix, iy + ih - bw, iw, bw, color);
        CustomRectRenderer.drawRect(guiGraphics, ix, iy, bw, ih, color);
        CustomRectRenderer.drawRect(guiGraphics, ix + iw - bw, iy, bw, ih, color);

        Gemini.hudDragManager.registerDragRegion(module, ix, iy, iw, ih);
    }

    /**
     * 渲染入口：按 Notification 模块的 Mode 配置分发。
     * Classic —— 深色磨砂共享容器；Mellow —— 浅色独立卡片。
     */
    public void renderAll(GuiGraphicsExtractor guiGraphics, Module module) {
        if (module instanceof Notification notif && notif.isMellow()) {
            renderMellow(guiGraphics, notif);
            return;
        }
        renderClassic(guiGraphics, module);
    }

    private void renderClassic(GuiGraphicsExtractor guiGraphics, Module module) {
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
                CustomAcrylicRenderer.drawFrostedRoundedRect(guiGraphics,
                        (int) exitBlurStartX, (int) currentBlurY,
                        (int) exitBlurStartWidth, (int) currentBlurHeight,
                        4, currentTint, 0.03f);
            }

            if (exitBlurProgress >= 1.0f) {
                exitBlurActive = false;
                exitBlurProgress = 0f;
            }
        } else if (!notifications.isEmpty()) {
            // 常规渲染：只有列表非空时才绘制模糊
            CustomAcrylicRenderer.drawFrostedRoundedRect(guiGraphics,
                    (int) blurX, (int) blurY,
                    (int) blurWidth, (int) blurHeight,
                    4, 0xD9141414, 0.03f);
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

    /**
     * Mellow 样式布局：每张通知是独立的浅色卡片（自带背景与 GLSL 投影），
     * 因此不绘制 Classic 的共享磨砂容器；锚点、平滑与对齐逻辑保持一致。
     */
    private void renderMellow(GuiGraphicsExtractor guiGraphics, Notification module) {
        // 卡片样式不使用共享背景，切模式时复位 Classic 的退场模糊状态
        exitBlurActive = false;
        exitBlurProgress = 0f;

        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        notifications.removeIf(ModuleNotification::isExpired);

        // --- 计算总高度和最大卡片宽度 ---
        float totalHeight = 0f;
        float maxWidth = 0f;
        for (ModuleNotification n : notifications) {
            totalHeight += MellowNotificationRenderer.CARD_HEIGHT + PADDING_Y;
            float w = MellowNotificationRenderer.cardWidth(n);
            if (w > maxWidth) maxWidth = w;
        }

        if (notifications.isEmpty()) {
            smoothYOffset = originY - MARGIN;
            lastTargetYOffset = smoothYOffset;
            smoothMaxWidth = 0f;
            return;
        }

        float targetY = originY - MARGIN - totalHeight + PADDING_Y;
        if (lastTargetYOffset == 0f) lastTargetYOffset = targetY;

        // Y 轴平滑（与 Classic 同参数）
        smoothYOffset = Mth.lerp(0.12f, smoothYOffset, targetY);
        lastTargetYOffset = targetY;

        // 统一卡片宽度的平滑过渡（扩张快、收缩慢，参考图中各卡同宽）
        if (maxWidth > smoothMaxWidth) {
            smoothMaxWidth = Mth.lerp(0.15f, smoothMaxWidth, maxWidth);
        } else {
            smoothMaxWidth = Mth.lerp(0.08f, smoothMaxWidth, maxWidth);
        }

        float currentYOffset = smoothYOffset;

        // --- 自顶向下绘制卡片（最新在最上方），各自做滑入滑出 ---
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ModuleNotification notification = notifications.get(i);

            float targetX = rightAligned
                    ? originX - MARGIN - smoothMaxWidth
                    : originX + MARGIN;

            MellowNotificationRenderer.render(guiGraphics, notification, module,
                    targetX, currentYOffset, smoothMaxWidth);

            currentYOffset += MellowNotificationRenderer.CARD_HEIGHT + PADDING_Y;
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