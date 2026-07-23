package net.minecraft.client.renderer.gizmos;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DrawableGizmoPrimitives implements GizmoPrimitives {
    private final DrawableGizmoPrimitives.Group opaque = new DrawableGizmoPrimitives.Group(true);
    private final DrawableGizmoPrimitives.Group translucent = new DrawableGizmoPrimitives.Group(false);
    private boolean isEmpty = true;

    private DrawableGizmoPrimitives.Group getGroup(int color) {
        return ARGB.alpha(color) < 255 ? this.translucent : this.opaque;
    }

    @Override
    public void addPoint(Vec3 pos, int color, float size) {
        this.getGroup(color).points.add(new DrawableGizmoPrimitives.Point(pos, color, size));
        this.isEmpty = false;
    }

    @Override
    public void addLine(Vec3 start, Vec3 end, int color, float width) {
        this.getGroup(color).lines.add(new DrawableGizmoPrimitives.Line(start, end, color, width));
        this.isEmpty = false;
    }

    @Override
    public void addTriangleFan(Vec3[] points, int color) {
        this.getGroup(color).triangleFans.add(new DrawableGizmoPrimitives.TriangleFan(points, color));
        this.isEmpty = false;
    }

    @Override
    public void addQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        this.getGroup(color).quads.add(new DrawableGizmoPrimitives.Quad(a, b, c, d, color));
        this.isEmpty = false;
    }

    @Override
    public void addText(Vec3 pos, String text, TextGizmo.Style style) {
        this.getGroup(style.color()).texts.add(new DrawableGizmoPrimitives.Text(pos, text, style));
        this.isEmpty = false;
    }

    public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, boolean onTop) {
        if (!this.isEmpty) {
            submitNodeCollector.submitGizmoPrimitives(this.opaque, cameraRenderState, onTop);
            submitNodeCollector.submitGizmoPrimitives(this.translucent, cameraRenderState, onTop);
        }
    }

    public record Group(
        boolean opaque,
        List<DrawableGizmoPrimitives.Line> lines,
        List<DrawableGizmoPrimitives.Quad> quads,
        List<DrawableGizmoPrimitives.TriangleFan> triangleFans,
        List<DrawableGizmoPrimitives.Text> texts,
        List<DrawableGizmoPrimitives.Point> points
    ) {
        private Group(boolean opaque) {
            this(opaque, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    public record Line(Vec3 start, Vec3 end, int color, float width) {
    }

    public record Point(Vec3 pos, int color, float size) {
    }

    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
    }

    public record Text(Vec3 pos, String text, TextGizmo.Style style) {
    }

    public record TriangleFan(Vec3[] points, int color) {
    }
}
