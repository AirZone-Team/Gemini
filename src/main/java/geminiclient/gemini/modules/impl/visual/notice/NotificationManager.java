package geminiclient.gemini.modules.impl.visual.notice;

import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static geminiclient.gemini.base.MinecraftInstance.mc;

public class NotificationManager {

    private final List<ModuleNotification> notifications = new CopyOnWriteArrayList<>();

    private static final int PADDING_Y = 4;       // 通知之间的垂直间距
    private static final int MARGIN = 12;         // 距离屏幕右下角的边距

    public void addNotification(ModuleNotification.NotificationLevel level, String message,
                                long duration, boolean isEnabled, boolean showStatus) {
        notifications.add(new ModuleNotification(level, message, duration, isEnabled, showStatus));
    }

    public void renderAll(GuiGraphics guiGraphics) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float currentYOffset = (float) screenHeight - MARGIN;

        notifications.removeIf(ModuleNotification::isExpired);

        // 从最新的通知开始渲染（底部向上堆叠）
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ModuleNotification notification = notifications.get(i);

            float notificationWidth = notification.calculateTargetWidth();
            float notificationHeight = notification.getHeight();

            float targetX = (float) screenWidth - MARGIN - notificationWidth;
            float targetY = currentYOffset - notificationHeight;

            notification.render(guiGraphics, targetX, targetY);

            currentYOffset = targetY - PADDING_Y;
        }
    }
}