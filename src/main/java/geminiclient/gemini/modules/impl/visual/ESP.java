package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class ESP extends Module {
    private final ListValue modes = new ListValue("Modes","2D",new String[]{
            "2D","3D"
    });
    private final BoolValue showPlayers = new BoolValue("Players", true);
    private final BoolValue showMobs = new BoolValue("Mobs", false);
    private final BoolValue showAnimals = new BoolValue("Animals", false);
    private final BoolValue showInvis = new BoolValue("Invisible", true);
    private final ColorValue boxColor = new ColorValue("Box Color", 0xFFFF0000);
    private final ColorValue outlineColor = new ColorValue("Outline Color", 0xFF000000);
    private final FloatValue lineThickness = new FloatValue("Line Thickness", 1.0f, 0.5f, 4.0f);

    public ESP() {
        super("ESP", ModuleEnum.Visual);
        addValue(modes,showPlayers, showMobs, showAnimals, showInvis, boxColor, outlineColor, lineThickness);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.level == null || mc.player == null)
            return;

        Camera camera = mc.gameRenderer.getMainCamera();
        GuiGraphicsExtractor gui = event.guiGraphics();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!isValid(entity))
                continue;

            float[] bounds = projectBounds(entity, camera);
            if (bounds == null)
                continue;

            float minX = bounds[0], minY = bounds[1], maxX = bounds[2], maxY = bounds[3];
            int fillColor = boxColor.getColor();
            int outline = outlineColor.getColor();
            int t = Math.round(lineThickness.getValue());

            if (modes.is("2D"))
                draw2DBox(gui, minX, minY, maxX, maxY, t, fillColor, outline);
            else {
                RenderUtils.drawFilledBox(entity.getBoundingBox(),fillColor);
            }
        }
    }

    /**
     * Projects entity's AABB corners to screen space, returns [minX, minY, maxX, maxY] or null if off-screen.
     */
    private float[] projectBounds(Entity entity, Camera camera) {
        AABB bb = entity.getBoundingBox();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        double[][] corners = getDoubles(entity, partialTick, bb);

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;

        for (double[] c : corners) {
            Vec3 s = worldToScreen(c[0], c[1], c[2], camera);
            if (s != null) {
                any = true;
                minX = Math.min(minX, (float) s.x);
                minY = Math.min(minY, (float) s.y);
                maxX = Math.max(maxX, (float) s.x);
                maxY = Math.max(maxY, (float) s.y);
            }
        }

        return any ? new float[]{minX, minY, maxX, maxY} : null;
    }

    private static double[] @NonNull [] getDoubles(Entity entity, float partialTick, AABB bb) {
        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());

        double hw = bb.getXsize() / 2.0;
        double hh = bb.getYsize();
        double hd = bb.getZsize() / 2.0;

        return new double[][]{
                {x - hw, y,      z - hd},
                {x - hw, y,      z + hd},
                {x + hw, y,      z - hd},
                {x + hw, y,      z + hd},
                {x - hw, y + hh, z - hd},
                {x - hw, y + hh, z + hd},
                {x + hw, y + hh, z - hd},
                {x + hw, y + hh, z + hd},
        };
    }

    private Vec3 worldToScreen(double wx, double wy, double wz, Camera camera) {
        Vec3 cam = camera.position();
        double dx = wx - cam.x;
        double dy = wy - cam.y;
        double dz = wz - cam.z;

        // 修复：使用正确的偏航角和俯仰角
        double yaw = Math.toRadians(camera.yRot());
        double pitch = Math.toRadians(camera.xRot());

        double cosY = Math.cos(yaw), sinY = Math.sin(yaw);
        double cosP = Math.cos(pitch), sinP = Math.sin(pitch);

        // 修复：使用正确的 Yaw 轴旋转矩阵 (Minecraft X轴向右为负，需调整符号)
        double x1 = -dx * cosY - dz * sinY;
        double z1 = -dx * sinY + dz * cosY;

        // 修复：使用正确的 Pitch 轴旋转矩阵
        double y1 = dy * cosP + z1 * sinP;
        double z2 = -dy * sinP + z1 * cosP;

        // 如果坐标在摄像机后面，抛弃该点
        if (z2 < 0.05)
            return null;

        // 修复：Camera 并没有 getFov 方法，需要从 mc.options 获取
        double fov = camera.getFov();
        double hw = mc.getWindow().getGuiScaledWidth() / 2.0;
        double hh = mc.getWindow().getGuiScaledHeight() / 2.0;

        // 投影运算 (视锥体缩放)
        double scale = hh / (z2 * Math.tan(Math.toRadians(fov / 2.0)));

        // y1 在相机空间中正值代表上方，所以屏幕坐标使用减法
        return new Vec3(hw + x1 * scale, hh - y1 * scale, z2);
    }

    private void draw2DBox(GuiGraphicsExtractor gui, float minX, float minY, float maxX, float maxY, int t, int fillColor, int outlineColor) {
        int x1 = (int) minX, y1 = (int) minY, x2 = (int) maxX, y2 = (int) maxY;

        // Filled box with alpha
        int fill = (fillColor & 0x00FFFFFF) | 0x25000000;
        if (x2 - x1 > t * 2 && y2 - y1 > t * 2) {
            gui.fill(x1 + t, y1 + t, x2 - t, y2 - t, fill);
        }

        // Top edge
        gui.fill(x1, y1, x2, y1 + t, outlineColor);
        // Bottom edge
        gui.fill(x1, y2 - t, x2, y2, outlineColor);
        // Left edge
        gui.fill(x1, y1, x1 + t, y2, outlineColor);
        // Right edge
        gui.fill(x2 - t, y1, x2, y2, outlineColor);
    }

    private boolean isValid(Entity entity) {
        if (entity == null || entity == mc.player)
            return false;
        if (entity instanceof ArmorStand)
            return false;
        // 注意：根据你的 Boolean 封装如果用的是 .getValue() 而不是 .enabled，请在这里一并改过来
        if (!showInvis.enabled && entity.isInvisible())
            return false;
        if (!(entity instanceof LivingEntity))
            return false;

        if (entity instanceof Player)
            return showPlayers.enabled;
        if (entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
            return showMobs.enabled;
        if (entity instanceof Animal)
            return showAnimals.enabled;

        return false;
    }
}