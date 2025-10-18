package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static geminiclient.gemini.base.MinecraftInstance.mc;

public class NotificationManager {
    // 使用线程安全的列表，因为通知可能在不同的线程被添加/移除 (例如事件处理和渲染)
    private final List<ModuleNotification> notifications = new CopyOnWriteArrayList<>();

    // 通知的垂直间距
    private static final int PADDING_Y = 2;
    // 距离屏幕右侧和底部的边距
    private static final int MARGIN = 10;

    /**
     * 添加一个新的通知。
     * @param level 通知等级
     * @param message 消息内容
     * @param duration 持续时间 (毫秒)
     */
    public void addNotification(ModuleNotification.NotificationLevel level, String message, long duration,boolean isEnabled, boolean showStatus) {
        // 确保通知不是重复的 (可选的去重逻辑可以添加在这里)
        this.notifications.add(new ModuleNotification(level,message,duration,isEnabled,showStatus));
    }

    public void addNotification(ModuleNotification.NotificationLevel level, String message, long duration) {
        this.notifications.add(new ModuleNotification(level, message, duration));
    }

    /**
     * 渲染所有活动通知。
     * @param guiGraphics NeoForge 的绘制上下文
     */
    public void renderAll(GuiGraphics guiGraphics) {

        // 获取缩放后的屏幕宽度和高度
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 累计 Y 偏移量，从底部向上堆叠
        float currentYOffset = (float)screenHeight - MARGIN;

        // 1. 移除已过期的通知
        notifications.removeIf(ModuleNotification::isExpired);

        // 2. 从列表末尾 (最新的通知) 开始渲染，实现从底部向上堆叠
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ModuleNotification notification = notifications.get(i);

            // 使用 calculateTargetWidth 来获取未经过动画的完整宽度
            float notificationWidth = notification.calculateTargetWidth();
            float notificationHeight = notification.getHeight();

            // **目标 X 坐标 (Target X)**：通知最终停留的 X 坐标
            // 屏幕宽度 - 边距 - 通知总宽度 (这里不包含左右 2px 的绘制偏移)
            float targetX = (float)screenWidth - MARGIN - notificationWidth;

            // 计算 Y 坐标 (保持不变)
            float targetY = currentYOffset - notificationHeight;

            // 渲染通知，传入 targetX 和 targetY
            notification.render(guiGraphics, targetX, targetY);

            // 更新下一个通知的 Y 偏移量
            currentYOffset = targetY - PADDING_Y;
        }
    }
}