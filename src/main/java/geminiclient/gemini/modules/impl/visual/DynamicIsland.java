package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Anim;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Fonts;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3RenderUtils;
import geminiclient.gemini.modules.impl.visual.clickgui.md3.Md3Theme;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Locale;

/**
 * A Material 3 HUD island that morphs around the player's current context.
 * Urgent health and air information takes priority over navigation and
 * performance information, keeping the default state deliberately compact.
 */
public final class DynamicIsland extends Module {
    private static final int COMPACT_WIDTH = 174;
    private static final int COMPACT_HEIGHT = 36;
    private static final int EXPANDED_WIDTH = 232;
    private static final int EXPANDED_HEIGHT = 54;
    private static final int ICON_SIZE = 26;
    private static final long MOVEMENT_HOLD_MS = 1_250L;

    private static final int ERROR_CONTAINER = Md3Theme.rgb(0xFFDAD6);
    private static final int ON_ERROR_CONTAINER = Md3Theme.rgb(0x410002);
    private static final int AIR_CONTAINER = Md3Theme.rgb(0xCFE8FF);
    private static final int AIR_CONTENT = Md3Theme.rgb(0x004A77);

    public final ListValue mode = new ListValue(
            "Mode", "Adaptive", new String[]{"Adaptive", "Compact", "Expanded"});
    public final IntValue healthAlert = new IntValue("Health Alert", 35, 10, 60);
    public final BoolValue shadow = new BoolValue("Shadow", true);

    private final Md3Anim widthAnim = Md3Anim.mediumAnim();
    private final Md3Anim heightAnim = Md3Anim.mediumAnim();
    private final Md3Anim contentAnim = Md3Anim.shortAnim();

    private IslandState state;
    private long lastMovingAt;

    public DynamicIsland() {
        super("DynamicIsland", ModuleEnum.Visual);
        // The x coordinate is the island's centre so width morphs stay centred.
        hudX = -1;
        hudY = 8;
        widthAnim.snap(COMPACT_WIDTH);
        heightAnim.snap(COMPACT_HEIGHT);
        contentAnim.snap(1f);
        addValue(mode, healthAlert, shadow);
    }

    @Override
    public void onEnabled() {
        state = null;
        widthAnim.snap(COMPACT_WIDTH);
        heightAnim.snap(COMPACT_HEIGHT);
        contentAnim.snap(1f);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender2D(Render2DEvent event) {
        GuiGraphicsExtractor g = event.guiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        if (hudX < 0) {
            hudX = screenWidth / 2;
        }

        IslandState next = resolveState();
        if (next != state) {
            state = next;
            contentAnim.snap(0f);
            contentAnim.setTarget(1f);
        }

        boolean expanded = state != IslandState.IDLE;
        widthAnim.setTarget(expanded ? EXPANDED_WIDTH : COMPACT_WIDTH);
        heightAnim.setTarget(expanded ? EXPANDED_HEIGHT : COMPACT_HEIGHT);

        int width = Math.round(widthAnim.getValue());
        int height = Math.round(heightAnim.getValue());
        hudX = Math.max(width / 2, Math.min(screenWidth - width / 2, hudX));
        int x = hudX - width / 2;
        int y = hudY;

        drawContainer(g, x, y, width, height);
        g.enableScissor(x, y, x + width, y + height);
        if (state == IslandState.IDLE) {
            drawCompact(g, x, y, width, height);
        } else {
            drawExpanded(g, x, y, width, height, state);
        }
        g.disableScissor();

        Gemini.hudDragManager.registerDragRegion(this, x, y, width, height);
    }

    @Override
    public void renderEditorOutline(GuiGraphicsExtractor g) {
        int width = Math.max(COMPACT_WIDTH, Math.round(widthAnim.getValue()));
        int height = Math.max(COMPACT_HEIGHT, Math.round(heightAnim.getValue()));
        int x = hudX - width / 2;
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, x, hudY, width, height, height / 2, 0xAAFFD700, 2);
        Gemini.hudDragManager.registerDragRegion(this, x, hudY, width, height);
    }

    private void drawContainer(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        int radius = height / 2;
        if (shadow.enabled) {
            Md3Theme.elevation2(g, x, y, width, height, radius);
        }
        CustomRoundedRectRenderer.drawRoundedRect(
                g, x, y, width, height, radius, Md3Theme.SURFACE_CONTAINER_LOWEST);
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, x, y, width, height, radius,
                Md3Theme.withAlpha(Md3Theme.OUTLINE_VARIANT, 0.72f), 1);
    }

    private void drawCompact(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        int centerY = y + height / 2;
        int iconCenterX = x + 8 + ICON_SIZE / 2;
        drawIconDisc(g, iconCenterX, centerY,
                Md3Theme.PRIMARY_CONTAINER, Md3Theme.PRIMARY, IslandState.IDLE, 1f);

        CustomFontRenderer.GlyphFont titleFont = Md3Fonts.title();
        float titleY = y + (height - Md3Fonts.lineHeight(titleFont)) / 2f;
        Md3Fonts.drawText(g, titleFont, "Gemini", x + 42, titleY, Md3Theme.ON_SURFACE);

        String metrics = fpsText() + "  /  " + pingText();
        CustomFontRenderer.GlyphFont labelFont = Md3Fonts.label();
        float metricsWidth = Md3Fonts.width(labelFont, metrics);
        Md3Fonts.drawText(g, labelFont, metrics,
                x + width - 10 - metricsWidth,
                y + (height - Md3Fonts.lineHeight(labelFont)) / 2f,
                Md3Theme.ON_SURFACE_VARIANT);
    }

    private void drawExpanded(GuiGraphicsExtractor g, int x, int y, int width, int height,
                              IslandState current) {
        float alpha = 0.35f + contentAnim.getValue() * 0.65f;
        int centerY = y + height / 2;
        int iconCenterX = x + 9 + ICON_SIZE / 2;
        int containerColor = containerColor(current);
        int contentColor = contentColor(current);
        drawIconDisc(g, iconCenterX, centerY, containerColor, contentColor, current, alpha);

        String title = title(current);
        String supporting = supportingText(current);
        CustomFontRenderer.GlyphFont titleFont = Md3Fonts.title();
        CustomFontRenderer.GlyphFont labelFont = Md3Fonts.label();
        int textX = x + 46;
        Md3Fonts.drawText(g, titleFont, title, textX, y + 8,
                Md3Theme.modulateAlpha(Md3Theme.ON_SURFACE, alpha));
        Md3Fonts.drawText(g, labelFont, supporting, textX, y + 27,
                Md3Theme.modulateAlpha(Md3Theme.ON_SURFACE_VARIANT, alpha));

        float progress = progress(current);
        int trackX = textX;
        int trackY = y + height - 9;
        int trackWidth = Math.max(1, width - (textX - x) - 13);
        CustomRoundedRectRenderer.drawRoundedRect(g, trackX, trackY, trackWidth, 3, 2,
                Md3Theme.SURFACE_CONTAINER_HIGHEST);
        CustomRoundedRectRenderer.drawRoundedRect(g, trackX, trackY,
                Math.max(3, Math.round(trackWidth * progress)), 3, 2,
                Md3Theme.modulateAlpha(contentColor, alpha));
    }

    private void drawIconDisc(GuiGraphicsExtractor g, int cx, int cy,
                              int container, int content, IslandState iconState, float alpha) {
        int bg = Md3Theme.modulateAlpha(container, alpha);
        int fg = Md3Theme.modulateAlpha(content, alpha);
        CustomRoundedRectRenderer.drawCircle(g, cx, cy, ICON_SIZE, bg);

        switch (iconState) {
            case HEALTH -> SdfUIRenderer.drawIcon(
                    g, cx, cy, 14, SdfUIRenderer.ICON_HEART_FILLED, fg);
            case AIR -> {
                CustomRoundedRectRenderer.drawCircle(g, cx - 4, cy + 3, 5, fg);
                CustomRoundedRectRenderer.drawCircle(g, cx + 2, cy - 3, 4, fg);
                CustomRoundedRectRenderer.drawCircle(g, cx + 6, cy + 4, 3, fg);
            }
            case MOVING -> {
                CustomRoundedRectRenderer.drawRoundedRect(g, cx - 1, cy - 7, 2, 14, 1, fg);
                CustomRoundedRectRenderer.drawRoundedRect(g, cx - 7, cy - 1, 14, 2, 1, fg);
                CustomRoundedRectRenderer.drawCircle(g, cx, cy, 5, container);
                CustomRoundedRectRenderer.drawCircle(g, cx, cy, 2, fg);
            }
            case PERFORMANCE -> {
                for (int i = 0; i < 3; i++) {
                    int barHeight = 5 + i * 3;
                    CustomRoundedRectRenderer.drawRoundedRect(
                            g, cx - 7 + i * 6, cy + 6 - barHeight,
                            3, barHeight, 2, fg);
                }
            }
            case IDLE -> Md3RenderUtils.drawSparkle(g, cx, cy, 12, fg);
        }
    }

    private IslandState resolveState() {
        if (mode.is("Compact") || mc.player == null) {
            return IslandState.IDLE;
        }

        float maxHealth = Math.max(1f, mc.player.getMaxHealth());
        if (mc.player.getHealth() / maxHealth * 100f <= healthAlert.getValue()) {
            return IslandState.HEALTH;
        }

        int maxAir = mc.player.getMaxAirSupply();
        if (maxAir > 0 && mc.player.getAirSupply() < maxAir) {
            return IslandState.AIR;
        }

        if (mode.is("Expanded")) {
            return IslandState.PERFORMANCE;
        }

        long now = System.currentTimeMillis();
        if (mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0025) {
            lastMovingAt = now;
        }
        return now - lastMovingAt <= MOVEMENT_HOLD_MS ? IslandState.MOVING : IslandState.IDLE;
    }

    private static String title(IslandState current) {
        return switch (current) {
            case HEALTH -> "Low health";
            case AIR -> "Air supply";
            case MOVING -> "Exploring";
            case PERFORMANCE -> "Performance";
            case IDLE -> "Gemini";
        };
    }

    private static String supportingText(IslandState current) {
        if (mc.player == null) {
            return "Ready";
        }
        return switch (current) {
            case HEALTH -> String.format(Locale.ROOT, "%.0f HP remaining", mc.player.getHealth());
            case AIR -> Math.round(progress(current) * 100f) + "% remaining";
            case MOVING -> String.format(Locale.ROOT, "X %.0f  /  Y %.0f  /  Z %.0f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ());
            case PERFORMANCE -> fpsText() + "  /  " + pingText();
            case IDLE -> fpsText() + "  /  " + pingText();
        };
    }

    private static float progress(IslandState current) {
        if (mc.player == null) {
            return 1f;
        }
        return switch (current) {
            case HEALTH -> clamp01(mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth()));
            case AIR -> clamp01(mc.player.getAirSupply() / (float) Math.max(1, mc.player.getMaxAirSupply()));
            case MOVING -> clamp01((float) Math.sqrt(
                    mc.player.getDeltaMovement().horizontalDistanceSqr()) / 0.28f);
            case PERFORMANCE -> clamp01(mc.getFps() / 144f);
            case IDLE -> 1f;
        };
    }

    private static int containerColor(IslandState current) {
        return switch (current) {
            case HEALTH -> ERROR_CONTAINER;
            case AIR -> AIR_CONTAINER;
            case MOVING -> Md3Theme.TERTIARY_CONTAINER;
            case PERFORMANCE, IDLE -> Md3Theme.PRIMARY_CONTAINER;
        };
    }

    private static int contentColor(IslandState current) {
        return switch (current) {
            case HEALTH -> ON_ERROR_CONTAINER;
            case AIR -> AIR_CONTENT;
            case MOVING -> Md3Theme.ON_TERTIARY_CONTAINER;
            case PERFORMANCE, IDLE -> Md3Theme.PRIMARY;
        };
    }

    private static String fpsText() {
        return Math.max(0, mc.getFps()) + " fps";
    }

    private static String pingText() {
        if (mc.player == null || mc.getConnection() == null) {
            return "-- ms";
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? "-- ms" : Math.max(0, info.getLatency()) + " ms";
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private enum IslandState {
        IDLE,
        HEALTH,
        AIR,
        MOVING,
        PERFORMANCE
    }
}
