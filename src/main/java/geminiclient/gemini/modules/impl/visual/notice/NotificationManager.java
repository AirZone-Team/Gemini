package geminiclient.gemini.modules.impl.visual.notice;

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

    private float lastTargetYOffset = 0f;
    private float smoothYOffset = 0f;

    public void addNotification(ModuleNotification.NotificationLevel level, String message,
                                long duration, boolean isEnabled, boolean showStatus) {
        notifications.add(new ModuleNotification(level, message, duration, isEnabled, showStatus));
    }

    public void renderAll(GuiGraphicsExtractor guiGraphics) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float currentYOffset = (float) screenHeight - MARGIN;

        notifications.removeIf(ModuleNotification::isExpired);

        float targetY = currentYOffset;
        if (notifications.isEmpty()) {
            lastTargetYOffset = targetY;
            smoothYOffset = targetY;
            return;
        }

        float totalHeight = 0f;
        for (ModuleNotification n : notifications) {
            totalHeight += n.getHeight() + PADDING_Y;
        }

        targetY = screenHeight - MARGIN - totalHeight + PADDING_Y;
        if (lastTargetYOffset == 0f) lastTargetYOffset = targetY;

        // 将 lerp 因子从 0.15f 稍微降低到 0.12f，使堆叠动画更加丝滑[cite: 3]
        smoothYOffset = Mth.lerp(0.12f, smoothYOffset, targetY);
        lastTargetYOffset = targetY;

        currentYOffset = smoothYOffset;

        for (int i = notifications.size() - 1; i >= 0; i--) {
            ModuleNotification notification = notifications.get(i);
            float notificationWidth = notification.calculateTargetWidth();
            float notificationHeight = notification.getHeight();

            float targetX = (float) screenWidth - MARGIN - notificationWidth;
            float targetYNotif = currentYOffset;

            notification.render(guiGraphics, targetX, targetYNotif);

            currentYOffset += notificationHeight + PADDING_Y;
        }
    }
}