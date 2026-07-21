package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class Radar extends Module {

    // ==================== CONFIGURATION VALUES ====================

    // Radar dimensions & range
    public final IntValue   radarSize       = new IntValue("Size", 128, 64, 300);
    public final FloatValue range           = new FloatValue("Range", 32f, 8f, 128f);

    // Entity type filters
    public final CheckboxValue entityFilters = new CheckboxValue("Entity Filters", new BoolValue[]{
            new BoolValue("Players", true),
            new BoolValue("Mobs", false),
            new BoolValue("Animals", false),
            new BoolValue("Items", false),
            new BoolValue("Invisible", true)
    });

    // Grid
    public final BoolValue  showGrid        = new BoolValue("Grid", true);

    // Colors
    public final ColorValue bgColor         = new ColorValue("BG Tint", 0xC0121212);
    public final ColorValue borderColor     = new ColorValue("Border", 0xFF333333);
    public final ColorValue playerDotColor  = new ColorValue("Player Dot", 0xFF00BBFF);
    public final ColorValue playerRingColor = new ColorValue("Player Ring", 0x99FFFFFF);
    public final ColorValue gridColor       = new ColorValue("Grid", 0x18FFFFFF);
    public final ColorValue playerColor     = new ColorValue("Player", 0xFFFF4444);
    public final ColorValue mobColor        = new ColorValue("Mob", 0xFFFF6622);
    public final ColorValue animalColor     = new ColorValue("Animal", 0xFF43E096);
    public final ColorValue itemColor       = new ColorValue("Item", 0xFFAAAAAA);

    // ==================== CONSTANTS ====================

    private static final int   RADIUS        = 6;
    private static final float ENTITY_DOT_R  = 2.0f;
    private static final float PLAYER_DOT_R  = 3.0f;
    private static final float PLAYER_RING_R = 6.0f;
    private static final float GRID_INTERVAL = 10f; // blocks between grid rings

    // ==================== CONSTRUCTOR ====================

    public Radar() {
        super("Radar", ModuleEnum.Visual);
        hudX = 6;
        hudY = 50;
        addValue(radarSize, range,
                entityFilters, showGrid,
                bgColor, borderColor, playerDotColor, playerRingColor, gridColor,
                playerColor, mobColor, animalColor, itemColor);
    }

    // ==================== EVENT HANDLER ====================

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        GuiGraphicsExtractor g = event.guiGraphics();
        int size = radarSize.getValue();
        int x = hudX;
        int y = hudY;

        // --- Background ---
        CustomRoundedRectRenderer.drawRoundedRect(g, x, y, size, size, RADIUS, bgColor.getColor());
        CustomRoundedRectRenderer.drawRoundedOutline(g, x, y, size, size, RADIUS, borderColor.getColor(), 1);

        // --- Collect visible entities ---
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (isValid(entity)) entities.add(entity);
        }

        float rangeVal = Math.max(range.getValue(), 1f);

        // --- Draw radar content clipped to background rounded-rect area ---
        g.enableScissor(x, y, x + size, y + size);
        drawRadarContent(g, x, y, size, entities, rangeVal);
        g.disableScissor();

        Gemini.hudDragManager.registerDragRegion(this, x, y, size, size);
    }

    // ==================== RADAR CONTENT RENDERING ====================

    private void drawRadarContent(GuiGraphicsExtractor g, int x, int y, int size,
                                  List<Entity> entities, float range) {
        if (mc.player == null) return;
        int cx = x + size / 2;
        int cy = y + size / 2;
        float scale = (size / 2f) / range;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = Mth.lerp(partialTick, mc.player.yRotO, mc.player.getYRot());
        double yawRad = Math.toRadians(yaw);
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);

        float px = (float) Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        float pz = (float) Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());

        // ── 1. Grid rings & cross lines ──
        if (showGrid.enabled) {
            int gridCol = gridColor.getColor();
            float gridStep = scale * GRID_INTERVAL;
            if (gridStep >= 4f) {
                for (float r = gridStep; r < size / 2f; r += gridStep) {
                    int ri = Math.round(r);
                    CustomRoundedRectRenderer.drawRoundedOutline(
                            g, cx - ri, cy - ri, ri * 2, ri * 2, ri, gridCol, 1);
                }
                // Cross lines through center
                CustomRectRenderer.drawRect(g, x, cy, size, 1, gridCol);
                CustomRectRenderer.drawRect(g, cx, y, 1, size, gridCol);
            }
        }

        // ── 2. Entity dots ──
        int dotSize = Math.max(1, Math.round(ENTITY_DOT_R * 2));
        int dotR = Math.round(ENTITY_DOT_R);
        for (Entity entity : entities) {
            float ex = (float) Mth.lerp(partialTick, entity.xo, entity.getX()) - px;
            float ez = (float) Mth.lerp(partialTick, entity.zo, entity.getZ()) - pz;

            float rx = -(ex * cos + ez * sin);
            float rz = -ex * sin + ez * cos;

            float sx = cx + rx * scale;
            float sy = cy - rz * scale;

            if (!isWithinRoundedRect(sx, sy, x, y, size, RADIUS, ENTITY_DOT_R))
                continue;

            int color = getEntityColor(entity);
            int dx = Math.round(sx - dotR);
            int dy = Math.round(sy - dotR);
            CustomRoundedRectRenderer.drawRoundedRect(g, dx, dy, dotSize, dotSize, dotR, color);
        }

        // ── 3. Player marker (outer ring + inner dot) ──
        int ringSize = Math.round(PLAYER_RING_R * 2);
        int ringR = Math.round(PLAYER_RING_R);
        CustomRoundedRectRenderer.drawRoundedOutline(
                g, cx - ringR, cy - ringR, ringSize, ringSize, ringR,
                playerRingColor.getColor(), 1);

        int plDotSize = Math.max(1, Math.round(PLAYER_DOT_R * 2));
        int plDotR = Math.round(PLAYER_DOT_R);
        CustomRoundedRectRenderer.drawRoundedRect(
                g, cx - plDotR, cy - plDotR, plDotSize, plDotSize, plDotR,
                playerDotColor.getColor());
    }

    // ==================== CLIPPING ====================

    /**
     * Checks whether a point (entity dot center) is within the rounded-rectangle
     * area defined by the radar background, accounting for the dot's own radius
     * so the rendered dot does not bleed past the rounded corners.
     */
    private static boolean isWithinRoundedRect(float sx, float sy,
                                               int x, int y, int size,
                                               int bgR, float dotR) {
        // Conservative AABB rejection with dot-radius margin
        float m = dotR + 0.5f;
        if (sx < x + m || sx > x + size - m || sy < y + m || sy > y + size - m)
            return false;

        if (bgR <= 0) return true;

        // ── Four corner zones: test distance from corner arc center ──
        int cx, cy;
        if (sx < x + bgR) {
            if (sy < y + bgR) { cx = x + bgR; cy = y + bgR; }          // TL
            else if (sy > y + size - bgR) { cx = x + bgR; cy = y + size - bgR; } // BL
            else return true;
        } else if (sx > x + size - bgR) {
            if (sy < y + bgR) { cx = x + size - bgR; cy = y + bgR; }   // TR
            else if (sy > y + size - bgR) { cx = x + size - bgR; cy = y + size - bgR; } // BR
            else return true;
        } else {
            return true; // In edge zone or inner rect, always safe
        }

        float dx = sx - cx;
        float dy = sy - cy;
        return dx * dx + dy * dy <= (bgR - dotR) * (bgR - dotR);
    }

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {
        int size = radarSize.getValue();
        int x = hudX;
        int y = hudY;
        CustomRoundedRectRenderer.drawRoundedOutline(g, x, y, size, size, RADIUS, 0xAAFFD700, 2);

        if (!enabled) {
            CustomRoundedRectRenderer.drawRoundedRect(g, x, y, size, size, RADIUS, 0x22141414);
        }

        Gemini.hudDragManager.registerDragRegion(this, x, y, size, size);
    }

    // ==================== ENTITY FILTERING ====================

    /**
     * Checks whether the given entity should be rendered on the radar
     * based on the current filter settings.
     */
    private boolean isValid(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        if (entity instanceof ArmorStand) return false;
        if (!entityFilters.boolValues[4].enabled && entity.isInvisible()) return false;

        if (entity instanceof Player) return entityFilters.boolValues[0].enabled;
        if (entity instanceof Animal) return entityFilters.boolValues[2].enabled;
        if (entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
            return entityFilters.boolValues[1].enabled;

        // Items, XP orbs, projectiles, etc.
        // 导入 net.minecraft.world.entity.item.ItemEntity;
        if (entity instanceof ItemEntity) {
            return entityFilters.boolValues[3].enabled;
        }

// 如果是不认识的实体（如矿车、箭矢等），默认不显示
        return false;
    }

    /**
     * Returns the dot color for the given entity based on its type.
     */
    private int getEntityColor(Entity entity) {
        if (entity instanceof Player) return playerColor.getColor();
        if (entity instanceof Animal) return animalColor.getColor();
        if (entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
            return mobColor.getColor();
        return itemColor.getColor();
    }
}
