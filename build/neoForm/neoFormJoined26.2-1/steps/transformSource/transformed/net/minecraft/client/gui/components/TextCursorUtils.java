package net.minecraft.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TextCursorUtils {
    public static final int CURSOR_INSERT_WIDTH = 1;
    private static final String CURSOR_APPEND_CHARACTER = "_";
    private static final int CURSOR_BLINK_INTERVAL_MS = 300;

    public static void extractInsertCursor(GuiGraphicsExtractor graphics, int x, int y, int color, int lineHeight) {
        graphics.fill(x, y - 1, x + 1, y + lineHeight, color);
    }

    public static void extractAppendCursor(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, boolean shadow) {
        graphics.text(font, "_", x, y, color, shadow);
    }

    public static boolean isCursorVisible(long timeInMs) {
        return timeInMs / 300L % 2L == 0L;
    }
}
