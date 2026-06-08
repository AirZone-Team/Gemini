package geminiclient.gemini.customRenderer.glsl.modules;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.util.function.Consumer;

import static geminiclient.gemini.base.MinecraftInstance.mc;
import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * GPU-accelerated sky lantern (孔明灯) renderer — batched draw calls.
 *
 * <p>Mesh (all in one buffer per lantern):
 * <ul>
 *   <li>Paper shell — 5 ring profile, 24 quads, warm translucent paper tint</li>
 *   <li>Bamboo frame rings — thin hoops at equator + top + bottom, darker wood tint</li>
 *   <li>Candle tray — small disc below the opening</li>
 *   <li>Suspension strings — 4 thin quads from opening to candle tray</li>
 * </ul>
 *
 * <p>Flame system (separate batches):
 * <ul>
 *   <li>Inner flame — small bright white-yellow billboard</li>
 *   <li>Outer flame — medium orange-red billboard</li>
 *   <li>Glow sprite — large radial-gradient billboard</li>
 *   <li>Ember sparks — tiny billboard quads drifting upward</li>
 * </ul>
 *
 */
public final class SkyLanternRenderer {

    private SkyLanternRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Lantern mesh — paper shell + bamboo frame + tray + strings
    // ════════════════════════════════════════════════════════════════

    public static final float[] LANTERN_MESH;
    public static final int    LANTERN_VERTS;

    /** Per-vertex material: 0=paper, 1=frame, 2=candle-tray, 3=string */
    public static final int[] LANTERN_MATERIAL;

    static {
        // Profile rings: {y, radius}
        float[][] rings = {
            { 1.00f, 0.12f },  // top
            { 0.50f, 0.70f },  // shoulder
            { 0.00f, 1.00f },  // equator
            {-0.45f, 0.58f },  // lower
            {-0.55f, 0.24f },  // opening
        };
        int slices = 4;
        double PI2 = Math.PI / 2.0;

        // Count quads
        int paperTop    = slices;              // top closure
        int paperBands  = (rings.length - 1) * slices; // 4 bands × 4
        int frameRings  = 3 * slices;          // equator + top + opening
        int tray        = slices;              // candle tray disc
        int strings     = slices;              // 4 strings
        int totalQuads  = paperTop + paperBands + frameRings + tray + strings;
        int totalVerts  = totalQuads * 4;

        LANTERN_VERTS   = totalVerts;
        LANTERN_MESH    = new float[totalVerts * 3];
        LANTERN_MATERIAL = new int[totalVerts];

        // ── Paper top closure (degenerate quads at pole) ──
        float poleY = 1.10f;
        float r0 = rings[0][1], y0 = rings[0][0];
        for (int s = 0; s < slices; s++) {
            double a1 = s * PI2, a2 = (s + 1) % slices * PI2;
            addV(0, poleY, 0, 0);
            addV((float)(r0*Math.cos(a1)), y0, (float)(r0*Math.sin(a1)), 0);
            addV((float)(r0*Math.cos(a2)), y0, (float)(r0*Math.sin(a2)), 0);
            addV(0, poleY, 0, 0);
        }

        // ── Paper body bands ──
        for (int b = 0; b < rings.length - 1; b++) {
            float rt = rings[b][1], yt = rings[b][0];
            float rb = rings[b+1][1], yb = rings[b+1][0];
            for (int s = 0; s < slices; s++) {
                double a1 = s * PI2, a2 = (s + 1) % slices * PI2;
                addV((float)(rt*Math.cos(a1)), yt, (float)(rt*Math.sin(a1)), 0);
                addV((float)(rt*Math.cos(a2)), yt, (float)(rt*Math.sin(a2)), 0);
                addV((float)(rb*Math.cos(a2)), yb, (float)(rb*Math.sin(a2)), 0);
                addV((float)(rb*Math.cos(a1)), yb, (float)(rb*Math.sin(a1)), 0);
            }
        }

        // ── Bamboo frame rings (thin hoops, offset outward from paper) ──
        // Visible rings: shoulder (idx 1), equator (idx 2), opening (idx 4)
        int[] frameRingIdx = {1, 2, 4};
        float hoopWidth = 0.025f, hoopThick = 0.012f;
        for (int ri : frameRingIdx) {
            float rr = rings[ri][1], ry = rings[ri][0];
            float rOut = rr + hoopWidth;
            float yBot = ry - hoopThick, yTop = ry + hoopThick;
            for (int s = 0; s < slices; s++) {
                double a1 = s * PI2, a2 = (s + 1) % slices * PI2;
                // Quad facing outward: inner→outer at yBot→yTop
                addV((float)(rr*Math.cos(a1)),   yBot, (float)(rr*Math.sin(a1)),   1);
                addV((float)(rr*Math.cos(a2)),   yBot, (float)(rr*Math.sin(a2)),   1);
                addV((float)(rOut*Math.cos(a2)), yTop, (float)(rOut*Math.sin(a2)), 1);
                addV((float)(rOut*Math.cos(a1)), yTop, (float)(rOut*Math.sin(a1)), 1);
            }
        }

        // ── Candle tray (small flat disc below opening) ──
        float trayY = -0.62f, trayR = 0.14f;
        for (int s = 0; s < slices; s++) {
            double a1 = s * PI2, a2 = (s + 1) % slices * PI2;
            addV((float)(trayR*Math.cos(a1)), trayY, (float)(trayR*Math.sin(a1)), 2);
            addV((float)(trayR*Math.cos(a2)), trayY, (float)(trayR*Math.sin(a2)), 2);
            addV(0, trayY - 0.01f, 0, 2);
            addV(0, trayY - 0.01f, 0, 2);
        }

        // ── Suspension strings (thin vertical quads from opening to tray) ──
        float strTopR = rings[4][1], strTopY = rings[4][0];
        float strBotR = trayR * 0.85f, strBotY = trayY;
        float strWidth = 0.008f;
        for (int s = 0; s < slices; s++) {
            double ang = s * PI2 + PI2 / 2.0; // offset by 45°
            float cx = (float)Math.cos(ang), cz = (float)Math.sin(ang);
            // Thin quad facing the center axis
            float tx = cx * strTopR, tz = cz * strTopR;
            float bx = cx * strBotR, bz = cz * strBotR;
            float nx = -cz * strWidth, nz = cx * strWidth; // perpendicular offset
            addV(tx - nx, strTopY, tz - nz, 3);
            addV(tx + nx, strTopY, tz + nz, 3);
            addV(bx + nx, strBotY, bz + nz, 3);
            addV(bx - nx, strBotY, bz - nz, 3);
        }
    }

    // Accumulator for static init
    private static int _vi;
    private static void addV(float x, float y, float z, int mat) {
        int i = _vi;
        LANTERN_MESH[i]=x; LANTERN_MESH[i+1]=y; LANTERN_MESH[i+2]=z;
        LANTERN_MATERIAL[i/3]=mat;
        _vi = i+3;
    }

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    private static final DepthStencilState PARTICLE_DEPTH =
            new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false, -1.0F, -1.0F);

    /** Lantern body (paper + frame) — additive warm glow. */
    public static final RenderPipeline LANTERN_PIPE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/lantern_body"))
            .withVertexShader(getIdentifier("core/fireflies"))
            .withFragmentShader(getIdentifier("core/fireflies"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withDepthStencilState(PARTICLE_DEPTH)
            .withCull(false)
            .build();

    /** Inner flame — small bright white-yellow, additive. */
    public static final RenderPipeline FLAME_INNER = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/flame_inner"))
            .withVertexShader(getIdentifier("core/fireflies_particle"))
            .withFragmentShader(getIdentifier("core/fireflies_particle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withDepthStencilState(PARTICLE_DEPTH)
            .withCull(false)
            .build();

    /** Outer flame — medium orange-red, additive. */
    public static final RenderPipeline FLAME_OUTER = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/flame_outer"))
            .withVertexShader(getIdentifier("core/fireflies"))
            .withFragmentShader(getIdentifier("core/fireflies"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withDepthStencilState(PARTICLE_DEPTH)
            .withCull(false)
            .build();

    /** Glow sprite — large radial-gradient billboard. */
    public static final RenderPipeline GLOW_SPRITE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/glow_sprite"))
            .withVertexShader(getIdentifier("core/fireflies"))
            .withFragmentShader(getIdentifier("core/fireflies"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withDepthStencilState(PARTICLE_DEPTH)
            .withCull(false)
            .build();

    /** Ember spark — tiny bright dot, additive. */
    public static final RenderPipeline EMBER = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(getIdentifier("pipeline/ember"))
            .withVertexShader(getIdentifier("core/fireflies"))
            .withFragmentShader(getIdentifier("core/fireflies"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withColorTargetState(new ColorTargetState(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE,
                    SourceFactor.ONE, DestFactor.ZERO)))
            .withDepthStencilState(PARTICLE_DEPTH)
            .withCull(false)
            .build();

    // ── Render types ─────────────────────────────────────────────

    private static final RenderType LANTERN_TYPE   = renderType("gemini_lantern_body", LANTERN_PIPE);
    private static final RenderType FLAME_INNER_T  = renderType("gemini_flame_inner", FLAME_INNER);
    private static final RenderType FLAME_OUTER_T  = renderType("gemini_flame_outer", FLAME_OUTER);
    private static final RenderType GLOW_SPRITE_T  = renderType("gemini_glow_sprite", GLOW_SPRITE);
    private static final RenderType EMBER_T        = renderType("gemini_ember", EMBER);

    private static RenderType renderType(String name, RenderPipeline pipe) {
        return RenderType.create(name,
                RenderSetup.builder(pipe).sortOnUpload()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());
    }

    // ── Registration ─────────────────────────────────────────────

    public static void registerPipeline(Consumer<RenderPipeline> registry) {
        registry.accept(LANTERN_PIPE);
        registry.accept(FLAME_INNER);
        registry.accept(FLAME_OUTER);
        registry.accept(GLOW_SPRITE);
        registry.accept(EMBER);
    }

    // ════════════════════════════════════════════════════════════════
    //  Frame context (camera state, computed once per frame)
    // ════════════════════════════════════════════════════════════════

    public static final class FrameCtx {
        public final Matrix4f viewMatrix;
        public final float camX, camY, camZ;

        public FrameCtx() {
            var cam = mc.getEntityRenderDispatcher().camera;
            camX = (float) cam.position().x;
            camY = (float) cam.position().y;
            camZ = (float) cam.position().z;
            viewMatrix = mc.gameRenderer.getGameRenderState()
                    .levelRenderState.cameraRenderState.viewRotationMatrix;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Material tint helpers
    // ════════════════════════════════════════════════════════════════

    /** Paper shell — slightly warm off-white */
    private static final float[] TINT_PAPER = {0.93f, 0.82f, 0.60f};
    /** Bamboo frame — dark brown wood */
    private static final float[] TINT_FRAME = {0.35f, 0.18f, 0.05f};
    /** Candle tray — dark metal/ceramic */
    private static final float[] TINT_TRAY  = {0.25f, 0.15f, 0.08f};
    /** Suspension strings — thin dark line */
    private static final float[] TINT_STRING = {0.20f, 0.12f, 0.06f};

    private static final float[][] MATERIAL_TINTS = {
        TINT_PAPER, TINT_FRAME, TINT_TRAY, TINT_STRING
    };

    // ════════════════════════════════════════════════════════════════
    //  Batched draw: lantern bodies (paper + frame)
    // ════════════════════════════════════════════════════════════════

    /**
     * @param lanterns [x,y,z,size,r,g,b,alpha, ...] × count
     */
    public static void drawLanterns(float[] lanterns, int count, FrameCtx ctx) {
        if (count == 0) return;
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f vm = ctx.viewMatrix;
        float cx = ctx.camX, cy = ctx.camY, cz = ctx.camZ;

        for (int p = 0; p < count; p++) {
            int off = p * 8;
            float px = lanterns[off],     py = lanterns[off + 1], pz = lanterns[off + 2];
            float sz = lanterns[off + 3];
            float cr = lanterns[off + 4], cg = lanterns[off + 5], cb = lanterns[off + 6];
            float alpha = lanterns[off + 7];

            for (int v = 0; v < LANTERN_VERTS; v++) {
                int vo = v * 3;
                int mat = LANTERN_MATERIAL[v];
                float[] tint = MATERIAL_TINTS[mat];

                // Blend material tint with lantern color
                float vr, vg, vb;
                if (mat == 0) { // paper — use lantern color blended with paper tint
                    vr = cr * tint[0];
                    vg = cg * tint[1];
                    vb = cb * tint[2];
                } else { // frame/tray/string — use material tint modulated by lantern brightness
                    float bright = (cr + cg + cb) / 3f;
                    vr = tint[0] * (0.5f + bright * 0.5f);
                    vg = tint[1] * (0.5f + bright * 0.5f);
                    vb = tint[2] * (0.5f + bright * 0.5f);
                }

                int ir = (int)(vr * 255f), ig = (int)(vg * 255f), ib = (int)(vb * 255f);
                int ia = (int)(alpha * 255f);
                int rgba = (ia << 24) | (ir << 16) | (ig << 8) | ib;

                buf.addVertex(vm,
                        px + LANTERN_MESH[vo] * sz - cx,
                        py + LANTERN_MESH[vo + 1] * sz - cy,
                        pz + LANTERN_MESH[vo + 2] * sz - cz)
                        .setColor(rgba);
            }
        }
        LANTERN_TYPE.draw(buf.buildOrThrow());
    }

    // ════════════════════════════════════════════════════════════════
    //  Batched draw: flame layers + glow sprite + embers
    // ════════════════════════════════════════════════════════════════

    /** @param flames [x,y,z,size,r,g,b,alpha, ...] */
    public static void drawFlameInner(float[] flames, int count, FrameCtx ctx) {
        drawBillboardBatch(flames, count, ctx, FLAME_INNER_T);
    }

    public static void drawFlameOuter(float[] flames, int count, FrameCtx ctx) {
        drawBillboardBatch(flames, count, ctx, FLAME_OUTER_T);
    }

    /** Glow sprite — radial halo, always camera-facing. */
    public static void drawGlowSprites(float[] glows, int count, FrameCtx ctx) {
        drawBillboardBatch(glows, count, ctx, GLOW_SPRITE_T);
    }

    public static void drawEmbers(float[] embers, int count, FrameCtx ctx) {
        drawBillboardBatch(embers, count, ctx, EMBER_T);
    }

    // ── Internal: billboard quad batch ────────────────────────────

    private static void drawBillboardBatch(float[] particles, int count,
                                           FrameCtx ctx, RenderType type) {
        if (count == 0) return;
        BufferBuilder buf = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f vm = ctx.viewMatrix;
        float cx = ctx.camX, cy = ctx.camY, cz = ctx.camZ;

        for (int p = 0; p < count; p++) {
            int off = p * 8;
            float px = particles[off],     py = particles[off + 1], pz = particles[off + 2];
            float h  = particles[off + 3];
            int r = (int)(particles[off + 4] * 255f);
            int g = (int)(particles[off + 5] * 255f);
            int b = (int)(particles[off + 6] * 255f);
            int a = (int)(particles[off + 7] * 255f);
            int rgba = (a << 24) | (r << 16) | (g << 8) | b;

            float rx = px - cx, ry = py - cy, rz = pz - cz;
            buf.addVertex(vm, rx - h, ry - h, rz).setColor(rgba);
            buf.addVertex(vm, rx - h, ry + h, rz).setColor(rgba);
            buf.addVertex(vm, rx + h, ry + h, rz).setColor(rgba);
            buf.addVertex(vm, rx + h, ry - h, rz).setColor(rgba);
        }
        type.draw(buf.buildOrThrow());
    }
}
