package net.minecraft.client.renderer.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.MutableInt;

@OnlyIn(Dist.CLIENT)
public class OctreeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;

    public OctreeDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks) {
        Octree octree = this.minecraft.levelRenderer.sectionOcclusionGraph().getOctree();
        MutableInt count = new MutableInt(0);
        octree.visitNodes((node, fullyVisible, depth, isClose) -> this.renderNode(node, depth, fullyVisible, count, isClose), frustum, 32);
    }

    private void renderNode(Octree.Node node, int depth, boolean fullyVisible, MutableInt count, boolean isClose) {
        AABB aabb = node.getAABB();
        double xSize = aabb.getXsize();
        long size = Math.round(xSize / 16.0);
        if (size == 1L) {
            count.add(1);
            int color = isClose ? -16711936 : -1;
            Gizmos.billboardText(String.valueOf(count.intValue()), aabb.getCenter(), TextGizmo.Style.forColorAndCentered(color).withScale(4.8F));
        }

        long colorNum = size + 5L;
        Gizmos.cuboid(
            aabb.deflate(0.1 * depth),
            GizmoStyle.stroke(
                ARGB.colorFromFloat(
                    fullyVisible ? 0.4F : 1.0F, getColorComponent(colorNum, 0.3F), getColorComponent(colorNum, 0.8F), getColorComponent(colorNum, 0.5F)
                )
            )
        );
    }

    private static float getColorComponent(long size, float multiplier) {
        float minColor = 0.1F;
        return Mth.frac(multiplier * (float)size) * 0.9F + 0.1F;
    }
}
