package net.minecraft.gizmos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Gizmos {
    private static final ThreadLocal<@Nullable GizmoCollector> collector = new ThreadLocal<>();

    private Gizmos() {
    }

    public static Gizmos.TemporaryCollection withCollector(GizmoCollector collector) {
        Gizmos.TemporaryCollection result = new Gizmos.TemporaryCollection();
        Gizmos.collector.set(collector);
        return result;
    }

    public static GizmoProperties addGizmo(Gizmo gizmo) {
        GizmoCollector collector = Gizmos.collector.get();
        if (collector == null) {
            throw new IllegalStateException("Gizmos cannot be created here! No GizmoCollector has been registered.");
        } else {
            return collector.add(gizmo);
        }
    }

    public static GizmoProperties cuboid(AABB aabb, GizmoStyle style) {
        return cuboid(aabb, style, false);
    }

    public static GizmoProperties cuboid(AABB aabb, GizmoStyle style, boolean coloredCorner) {
        return addGizmo(new CuboidGizmo(aabb, style, coloredCorner));
    }

    public static GizmoProperties cuboid(BlockPos blockPos, GizmoStyle style) {
        return cuboid(new AABB(blockPos), style);
    }

    public static GizmoProperties cuboid(BlockPos blockPos, float padding, GizmoStyle style) {
        return cuboid(new AABB(blockPos).inflate(padding), style);
    }

    public static GizmoProperties circle(Vec3 pos, float radius, GizmoStyle style) {
        return addGizmo(new CircleGizmo(pos, radius, style));
    }

    public static GizmoProperties line(Vec3 start, Vec3 end, int argb) {
        return addGizmo(new LineGizmo(start, end, argb, 3.0F));
    }

    public static GizmoProperties line(Vec3 start, Vec3 end, int argb, float width) {
        return addGizmo(new LineGizmo(start, end, argb, width));
    }

    public static GizmoProperties arrow(Vec3 start, Vec3 end, int argb) {
        return addGizmo(new ArrowGizmo(start, end, argb, 2.5F));
    }

    public static GizmoProperties arrow(Vec3 start, Vec3 end, int argb, float width) {
        return addGizmo(new ArrowGizmo(start, end, argb, width));
    }

    public static GizmoProperties rect(Vec3 cuboidCornerA, Vec3 cuboidCornerB, Direction face, GizmoStyle style) {
        return addGizmo(RectGizmo.fromCuboidFace(cuboidCornerA, cuboidCornerB, face, style));
    }

    public static GizmoProperties rect(Vec3 cornerA, Vec3 cornerB, Vec3 cornerC, Vec3 cornerD, GizmoStyle style) {
        return addGizmo(new RectGizmo(cornerA, cornerB, cornerC, cornerD, style));
    }

    public static GizmoProperties point(Vec3 position, int argb, float size) {
        return addGizmo(new PointGizmo(position, argb, size));
    }

    public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int row, int color, float scale) {
        double firstRowStartPosition = 1.3;
        double rowHeight = 0.2;
        GizmoProperties properties = billboardText(
            text, Vec3.atLowerCornerWithOffset(pos, 0.5, 1.3 + row * 0.2, 0.5), TextGizmo.Style.forColorAndCentered(color).withScale(scale)
        );
        properties.setAlwaysOnTop();
        return properties;
    }

    public static GizmoProperties billboardTextOverMob(Entity entity, int row, String text, int color, float scale) {
        double firstRowStartPosition = 2.4;
        double rowHeight = 0.25;
        double x = entity.getBlockX() + 0.5;
        double y = entity.getY() + 2.4 + row * 0.25;
        double z = entity.getBlockZ() + 0.5;
        float textAdjustLeft = 0.5F;
        GizmoProperties properties = billboardText(text, new Vec3(x, y, z), TextGizmo.Style.forColor(color).withScale(scale).withLeftAlignment(0.5F));
        properties.setAlwaysOnTop();
        return properties;
    }

    public static GizmoProperties billboardText(String name, Vec3 pos, TextGizmo.Style style) {
        return addGizmo(new TextGizmo(pos, name, style));
    }

    public static class TemporaryCollection implements AutoCloseable {
        private final @Nullable GizmoCollector old = Gizmos.collector.get();
        private boolean closed;

        private TemporaryCollection() {
        }

        @Override
        public void close() {
            if (!this.closed) {
                this.closed = true;
                Gizmos.collector.set(this.old);
            }
        }
    }
}
