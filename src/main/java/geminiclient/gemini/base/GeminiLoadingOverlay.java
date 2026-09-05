package geminiclient.gemini.base;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import geminiclient.gemini.customRenderer.glsl.SdfUIRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import java.util.function.Consumer;

import static geminiclient.gemini.utils.ResourceLocationUtils.getIdentifier;

/**
 * 客户端自定义启动加载画面：在 {@code Minecraft.<init>} 中经 MixinMinecraft
 * 替换原版 {@code LoadingOverlay}，接管整个初始资源重载期间的展示。
 *
 * <p>视觉：灰色底 + 居中四角星（astroid 四尖内摆线，即参考图形状），无任何
 * 文字。重载期间以进度驱动两段动画——从左侧顶点起笔的闭合描边（进度条语义，
 * 领先于填充），以及沿同方向扫入的四色渐变填充（红/蓝/绿/橙对应四个顶点，
 * 中心为四色均值）。重载完成后星形满色并呼吸脉动，驻留画面与加载画面
 * 一模一样（同一张灰底），驻留等待任意按键或鼠标点击（输入 Mixin 转发给
 * {@link #onUserInput}）；确认后星形飞向左上角、灰色底随飞行淡出，主菜单
 * 此刻才显现，并把品牌标记常驻在主菜单左上角（{@link #drawMenuMark}）。</p>
 *
 * <p>渲染约束：资源重载完成前自定义管线与字形均不可用，任何 vanilla 文字
 * （gui_text）或 MSDF 绘制都会触发编译失败并永久缓存 invalid 管线导致崩溃。
 * 因此本画面只用纯 CPU 几何：三角形扇（填充）与条带（描边）以 POSITION_COLOR
 * 四边形提交，不引入任何新着色器——{@link #STAR_PIPELINE} 仅从
 * {@code GUI_SNIPPET} 派生并关闭背面剔除，着色器复用原版 {@code core/gui}。</p>
 *
 * <p>生命周期与原版一致：{@link #tick()} 在重载完成后调用
 * {@code checkExceptions()} 与 onFinish 消费者（失败回滚 / 成功流转主菜单），
 * 之后不淡出而是驻留；{@link #extractRenderState} 在过渡阶段把底层 screen
 * 淡入展示，最后自清除。</p>
 */
public class GeminiLoadingOverlay extends Overlay {

    // ========================
    // 视觉常量
    // ========================
    private static final int BG = 0xFF3A3E45;

    /** 四顶点锚色（对照参考图：左橙、上红、右蓝、下绿）。 */
    private static final int COLOR_LEFT = 0xFFF2A93B;
    private static final int COLOR_TOP = 0xFFE8434A;
    private static final int COLOR_RIGHT = 0xFF2E7CE6;
    private static final int COLOR_BOTTOM = 0xFF2FA356;
    /** 扇形中心色 = 四锚色均值，对应参考图的中心混色。 */
    private static final int COLOR_CENTER = averageColor(COLOR_LEFT, COLOR_TOP, COLOR_RIGHT, COLOR_BOTTOM);
    private static final int OUTLINE_COLOR = 0xFFE8ECF1;

    /** 路径离散段数（整圈）。 */
    private static final int STAR_SEGMENTS = 64;
    /** 描边线宽的一半（gui 像素）。 */
    private static final float OUTLINE_HALF_WIDTH = 0.9f;

    /** 驻留星形占屏幕短边的比例。 */
    private static final float STAR_SIZE_FACTOR = 0.23f;
    /** 主菜单左上角品牌标记的位置与半径。 */
    private static final float MARK_X = 21f;
    private static final float MARK_Y = 21f;
    private static final float MARK_RADIUS = 11f;

    // ========================
    // 节奏常量（毫秒）
    // ========================
    private static final long MIN_DISPLAY_MILLIS = 1000L;
    private static final long TRANSITION_TIME = 650L;

    /**
     * 渲染管线：GUI_SNIPPET 派生 + 关闭背面剔除（扇形与条带的绕向随路径
     * 变化，剔除会裁掉一半）。着色器与原版 GUI 管线相同，无新着色器。
     */
    private static final RenderPipeline STAR_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(getIdentifier("pipeline/loader_star"))
            .withCull(false)
            .build();

    /** 加载→主菜单交接时间戳；≥0 表示星形标记应绘制在主菜单左上角。 */
    private static volatile long menuMarkStart = -1L;

    /** Slang 星形管线是否已就绪（就绪前以 CPU 网格兜底绘制）。 */
    private static volatile boolean sdfReady;

    // ========================
    // 生命周期状态（结构与原版 LoadingOverlay 一致）
    // ========================
    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final boolean fadeIn;

    private float currentProgress;
    private final long createdMillis;
    private long fadeInStart = -1L;
    private boolean finishHandled;
    private long waitingSince = -1L;
    private long transitionStart = -1L;

    public GeminiLoadingOverlay(Minecraft minecraft, ReloadInstance reload,
                                Consumer<Optional<Throwable>> onFinish, boolean fadeIn) {
        this.minecraft = minecraft;
        this.reload = reload;
        this.onFinish = onFinish;
        this.fadeIn = fadeIn;
        this.createdMillis = Util.getMillis();
    }

    // ========================
    // 生命周期（原版语义 + 驻留等待）
    // ========================

    @Override
    public void tick() {
        if (!this.finishHandled && this.reload.isDone()
                && Util.getMillis() - this.createdMillis >= MIN_DISPLAY_MILLIS) {
            this.finishHandled = true;
            this.waitingSince = Util.getMillis();
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                this.onFinish.accept(Optional.of(t));
            }

            if (this.minecraft.gui.screen() != null) {
                this.minecraft.gui.screen().init(this.minecraft.getWindow().getGuiScaledWidth(),
                        this.minecraft.getWindow().getGuiScaledHeight());
            }
        }
    }

    /**
     * 用户确认（任意按键 / 鼠标按下，由输入 Mixin 转发）。仅在重载完成后的
     * 驻留阶段有效；返回 true 表示事件已消费，调用方应取消后续分发。
     */
    public boolean onUserInput(boolean isPress) {
        if (!isPress || !this.finishHandled || this.transitionStart > -1L) {
            return false;
        }
        this.transitionStart = Util.getMillis();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        long now = Util.getMillis();

        tryPrepareStarPipeline();

        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = now;
        }
        float transAnim = this.transitionStart > -1L
                ? (now - this.transitionStart) / (float) TRANSITION_TIME : -1.0F;
        float fadeInAnim = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500.0F : -1.0F;
        float flyE = transAnim >= 0.0F ? easeInOutCubic(Mth.clamp(transAnim, 0.0F, 1.0F)) : 0.0F;

        // 背景分支：加载与驻留共用同一张灰色底（驻留画面与加载画面一模一样，
        // 仅星形状态不同）；主菜单在确认之前绝不显现——按下按键/点击后才提取
        // 底层 screen，灰色底随星形飞行淡出。fadeIn=false 时只接管清屏色，
        // 与原版 LoadingOverlay 一致。
        if (transAnim >= 0.0F) {
            if (this.minecraft.gui.screen() != null) {
                this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(gui, 0, 0, partialTicks);
            } else {
                this.minecraft.gui.hud.extractDeferredSubtitles();
            }
            gui.nextStratum();
            gui.fill(0, 0, width, height, bgWithAlpha(1.0F - Mth.clamp(transAnim, 0.0F, 1.0F)));
        } else if (this.fadeIn) {
            if (this.minecraft.gui.screen() != null && fadeInAnim < 1.0F) {
                this.minecraft.gui.screen().extractRenderStateWithTooltipAndSubtitles(gui, mouseX, mouseY, partialTicks);
            } else {
                this.minecraft.gui.hud.extractDeferredSubtitles();
            }
            gui.nextStratum();
            gui.fill(0, 0, width, height, bgWithAlpha(Mth.clamp(fadeInAnim, 0.15F, 1.0F)));
        } else {
            ARGB.setVector4fFromARGB32(
                    this.minecraft.gameRenderer.gameRenderState().guiRenderState.clearColorOverride, BG);
        }

        if (transAnim >= 1.0F) {
            // 交接：主菜单从下一帧起自绘左上角星形标记，本 overlay 退场
            menuMarkStart = now;
            this.minecraft.gui.setOverlay(null);
            return;
        }

        // 平滑进度（原版同款收敛系数）
        float actual = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actual * 0.050000012F, 0.0F, 1.0F);

        // ── 星形参数 ──
        float radius = Math.min(width, height) * STAR_SIZE_FACTOR;
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float outlineP;
        float fillP;
        float breathe = 1.0F;
        if (this.finishHandled) {
            // 驻留：满色 + 缓慢呼吸
            outlineP = 1.0F;
            fillP = 1.0F;
            breathe = 1.0F + 0.018F * (float) Math.sin((now - this.waitingSince) / 500.0);
        } else {
            // 加载中：描边领先，填充沿同方向追赶
            outlineP = Mth.clamp(this.currentProgress * 1.12F, 0.0F, 1.0F);
            fillP = Mth.clamp(this.currentProgress * 1.12F - 0.22F, 0.0F, 1.0F);
        }

        float sx = transAnim >= 0.0F ? lerp(cx, MARK_X, flyE) : cx;
        float sy = transAnim >= 0.0F ? lerp(cy, MARK_Y, flyE) : cy;
        float sr = transAnim >= 0.0F ? lerp(radius, MARK_RADIUS, flyE) : radius;

        // 过渡期（星形飞越显现的主菜单时）垫一层高斯软阴影，随飞行淡出，
        // 落位后的常驻标记不带阴影；加载/驻留为纯灰底，无需阴影
        if (sdfReady && transAnim >= 0.0F) {
            float shadowA = 1.0F - flyE;
            if (shadowA > 0.01F) {
                int sw = Math.max(4, Math.round(sr * breathe * 2.8F));
                SdfUIRenderer.drawShadow(gui, Math.round(sx - sw / 2.0F), Math.round(sy - sw / 2.0F),
                        sw, sw, sw / 2, 0, 0, Math.max(2, sw / 6),
                        scaleAlpha(0xFF050709, 0.55F * shadowA));
            }
        }

        drawStar(gui, sx, sy, sr * breathe,
                transAnim >= 0.0F ? 1.0F : outlineP, transAnim >= 0.0F ? 1.0F : fillP);
    }

    /** 主菜单左上角的品牌星形标记；由 MainMenuScreen 每帧调用。 */
    public static void drawMenuMark(GuiGraphicsExtractor gui) {
        long start = menuMarkStart;
        if (start < 0L) {
            return;
        }
        float breathe = 1.0F + 0.05F * (float) Math.sin((Util.getMillis() - start) / 700.0);
        // 小尺寸标记不带描边：细线在 r=11 下会糊成白色菱形，纯渐变更贴近参考图
        drawStar(gui, MARK_X, MARK_Y, MARK_RADIUS * breathe, 0.0F, 1.0F);
    }

    /** SDF 管线就绪则走 fwidth 抗锯齿路径，否则退回 CPU 网格（仅最初几帧）。 */
    private static void drawStar(GuiGraphicsExtractor gui, float cx, float cy, float r,
                                 float outlineP, float fillP) {
        if (sdfReady) {
            SdfUIRenderer.drawLoaderStar(gui, cx, cy, r, outlineP, fillP);
        } else {
            drawStarMesh(gui, cx, cy, r, outlineP, fillP, 1.0F);
        }
    }

    /**
     * Slang 星形管线预编译。加载画面赶在重载监听器 warmup 之前渲染，而
     * ShaderManager 的着色器源在重载应用前不可用（首帧懒编译会像 gui_text
     * 那样缓存 invalid 管线并崩溃），因此用 UiShaderWarmup 同款的直连
     * ResourceManager 解析器就地预编译；资源尚未可取时下一帧重试，
     * 就绪前由 CPU 网格兜底。
     */
    private static void tryPrepareStarPipeline() {
        if (sdfReady) {
            return;
        }
        try {
            ShaderSource source = (id, type) -> {
                Identifier location = type.idConverter().idToFile(id);
                try (Reader reader = Minecraft.getInstance().getResourceManager()
                        .getResourceOrThrow(location).openAsReader()) {
                    return IOUtils.toString(reader);
                } catch (IOException e) {
                    return null;
                }
            };
            CompiledRenderPipeline compiled = RenderSystem.getDevice()
                    .precompilePipeline(SdfUIRenderer.SDF_STAR_PIPELINE, source);
            sdfReady = compiled != null && compiled.isValid();
        } catch (Throwable ignored) {
            sdfReady = false;
        }
    }

    // ========================
    // 星形几何与网格
    // ========================

    /**
     * 提交星形网格：三角形扇（填充，顶点色四色渐变）+ 沿曲线条带（描边）
     * + 描边笔尖亮块。全部以退化四边形走 {@link #STAR_PIPELINE}。
     */
    private static void drawStarMesh(GuiGraphicsExtractor gui, float cx, float cy, float r,
                                     float outlineP, float fillP, float alpha) {
        if (alpha <= 0.0F || r < 1.0F) {
            return;
        }
        int n = STAR_SEGMENTS;
        float[] xs = new float[n + 1];
        float[] ys = new float[n + 1];
        int[] cs = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            xs[i] = starX(s, cx, r);
            ys[i] = starY(s, cy, r);
            cs[i] = vertexColor(s);
        }

        // 填充扇 + 描边条带，最坏情况各 n 个四边形
        float[] positions = new float[2 * n * 4 * 2];
        byte[] colors = new byte[2 * n * 4 * 4];
        int[] quad = new int[4 * 2];
        int[] quadColors = new int[4];
        int quads = 0;

        if (fillP > 0.0F) {
            for (int i = 0; i < n; i++) {
                float s0 = i / (float) n;
                if (fillP <= s0) {
                    break;
                }
                float f = Mth.clamp((fillP - s0) * n * 0.8F, 0.0F, 1.0F) * alpha;
                putQuad(quad, quadColors,
                        cx, cy, COLOR_CENTER,
                        xs[i], ys[i], cs[i],
                        xs[i + 1], ys[i + 1], cs[i + 1]);
                quads = appendQuad(positions, colors, quads, quad, quadColors, f);
            }
        }

        if (outlineP > 0.0F) {
            for (int i = 0; i < n; i++) {
                float s0 = i / (float) n;
                if (outlineP <= s0) {
                    break;
                }
                float f = Mth.clamp((outlineP - s0) * n * 0.8F, 0.0F, 1.0F) * alpha;
                float tipF = Mth.clamp((outlineP - s0) * n, 0.0F, 1.0F);
                float x0 = xs[i];
                float y0 = ys[i];
                float x1;
                float y1;
                if (tipF >= 1.0F) {
                    x1 = xs[i + 1];
                    y1 = ys[i + 1];
                } else {
                    float s = s0 + (1.0F / n) * tipF;
                    x1 = starX(s, cx, r);
                    y1 = starY(s, cy, r);
                }
                float dx = x1 - x0;
                float dy = y1 - y0;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1.0e-4F) {
                    continue;
                }
                // 线宽随半径收敛：小尺寸星形（过渡落位）不至于被描边糊满
                float hw = Math.min(OUTLINE_HALF_WIDTH, r * 0.03F);
                float nx = -dy / len * hw;
                float ny = dx / len * hw;
                putQuad(quad, quadColors,
                        x0 + nx, y0 + ny, OUTLINE_COLOR,
                        x0 - nx, y0 - ny, OUTLINE_COLOR,
                        x1 - nx, y1 - ny, OUTLINE_COLOR,
                        x1 + nx, y1 + ny, OUTLINE_COLOR);
                quads = appendQuad(positions, colors, quads, quad, quadColors, f);

                if (tipF < 1.0F) {
                    // 起笔中的笔尖亮块
                    float h = OUTLINE_HALF_WIDTH + 0.8F;
                    putQuad(quad, quadColors,
                            x1 - h, y1 - h, OUTLINE_COLOR,
                            x1 - h, y1 + h, OUTLINE_COLOR,
                            x1 + h, y1 + h, OUTLINE_COLOR,
                            x1 + h, y1 - h, OUTLINE_COLOR);
                    quads = appendQuad(positions, colors, quads, quad, quadColors, alpha);
                }
            }
        }

        if (quads > 0) {
            gui.submitGuiElementRenderState(new MeshState(
                    new Matrix3x2f(gui.pose()), positions, colors, quads, gui.peekScissorStack()));
        }
    }

    /** 路径参数 s（0=左顶点，屏幕上呈 L→T→R→B→L 顺时针）处的 x。 */
    private static float starX(float s, float cx, float r) {
        int q = (int) (s * 4.0F);
        if (q > 3) {
            q = 3;
        }
        float u = s * 4.0F - q;
        float c = (float) Math.pow(Math.cos(u * Math.PI / 2.0), 3.0);
        float sn = (float) Math.pow(Math.sin(u * Math.PI / 2.0), 3.0);
        return switch (q) {
            case 0 -> cx - r * c;   // L → T
            case 1 -> cx + r * sn;  // T → R
            case 2 -> cx + r * c;   // R → B
            default -> cx - r * sn; // B → L
        };
    }

    private static float starY(float s, float cy, float r) {
        int q = (int) (s * 4.0F);
        if (q > 3) {
            q = 3;
        }
        float u = s * 4.0F - q;
        float c = (float) Math.pow(Math.cos(u * Math.PI / 2.0), 3.0);
        float sn = (float) Math.pow(Math.sin(u * Math.PI / 2.0), 3.0);
        return switch (q) {
            case 0 -> cy - r * sn;
            case 1 -> cy - r * c;
            case 2 -> cy + r * sn;
            default -> cy + r * c;
        };
    }

    /** 边界点颜色：四个顶点锚色之间按路径参数插值。 */
    private static int vertexColor(float s) {
        float t = s * 4.0F;
        int i = (int) t % 4;
        float f = t - (int) t;
        int a = switch (i) {
            case 0 -> COLOR_LEFT;
            case 1 -> COLOR_TOP;
            case 2 -> COLOR_RIGHT;
            default -> COLOR_BOTTOM;
        };
        int b = switch (i) {
            case 0 -> COLOR_TOP;
            case 1 -> COLOR_RIGHT;
            case 2 -> COLOR_BOTTOM;
            default -> COLOR_LEFT;
        };
        return lerpColor(a, b, f);
    }

    private static void putQuad(int[] quad, int[] quadColors,
                                float x0, float y0, int c0,
                                float x1, float y1, int c1,
                                float x2, float y2, int c2) {
        putQuad(quad, quadColors, x0, y0, c0, x1, y1, c1, x2, y2, c2, x2, y2, c2);
    }

    private static void putQuad(int[] quad, int[] quadColors,
                                float x0, float y0, int c0,
                                float x1, float y1, int c1,
                                float x2, float y2, int c2,
                                float x3, float y3, int c3) {
        quad[0] = Math.round(x0);
        quad[1] = Math.round(y0);
        quad[2] = Math.round(x1);
        quad[3] = Math.round(y1);
        quad[4] = Math.round(x2);
        quad[5] = Math.round(y2);
        quad[6] = Math.round(x3);
        quad[7] = Math.round(y3);
        quadColors[0] = c0;
        quadColors[1] = c1;
        quadColors[2] = c2;
        quadColors[3] = c3;
    }

    private static int appendQuad(float[] positions, byte[] colors, int quads,
                                  int[] quad, int[] quadColors, float alphaScale) {
        int vi = quads * 8;
        int ci = quads * 16;
        for (int v = 0; v < 4; v++) {
            positions[vi + v * 2] = quad[v * 2];
            positions[vi + v * 2 + 1] = quad[v * 2 + 1];
            int argb = scaleAlpha(quadColors[v], alphaScale);
            colors[ci + v * 4] = (byte) ((argb >> 16) & 0xFF);
            colors[ci + v * 4 + 1] = (byte) ((argb >> 8) & 0xFF);
            colors[ci + v * 4 + 2] = (byte) (argb & 0xFF);
            colors[ci + v * 4 + 3] = (byte) ((argb >>> 24) & 0xFF);
        }
        return quads + 1;
    }

    /**
     * 自定义 GUI 元素渲染状态：任意 POSITION_COLOR 四边形列表（三角形以
     * 重复末顶点退化）。管线无剔除、无纹理，坐标已约到整像素。
     */
    private record MeshState(Matrix3x2f pose, float[] positions, byte[] colors, int quadCount,
                             @Nullable ScreenRectangle scissor) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vc) {
            for (int v = 0; v < quadCount * 4; v++) {
                vc.addVertexWith2DPose(pose, positions[v * 2], positions[v * 2 + 1])
                        .setColor(colors[v * 4] & 0xFF, colors[v * 4 + 1] & 0xFF,
                                colors[v * 4 + 2] & 0xFF, colors[v * 4 + 3] & 0xFF);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return STAR_PIPELINE;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        @Nullable
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        @Nullable
        public ScreenRectangle bounds() {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            int vertexCount = quadCount * 4 * 2;
            for (int i = 0; i < vertexCount; i += 2) {
                float px = positions[i];
                float py = positions[i + 1];
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
            }
            ScreenRectangle bounds = new ScreenRectangle(
                    (int) Math.floor(minX), (int) Math.floor(minY),
                    Math.max(1, (int) Math.ceil(maxX) - (int) Math.floor(minX)),
                    Math.max(1, (int) Math.ceil(maxY) - (int) Math.floor(minY)));
            return scissor != null ? scissor.intersection(bounds) : bounds;
        }
    }

    // ========================
    // 工具
    // ========================

    private static int averageColor(int... colors) {
        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        for (int c : colors) {
            a += c >>> 24;
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int count = colors.length;
        return ARGB.color(Math.round(a / (float) count),
                Math.round(r / (float) count), Math.round(g / (float) count), Math.round(b / (float) count));
    }

    private static int lerpColor(int a, int b, float t) {
        float tp = Math.clamp(t, 0f, 1f);
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * tp) << 24)
                | (Math.round(ar + (br - ar) * tp) << 16)
                | (Math.round(ag + (bg - ag) * tp) << 8)
                | Math.round(ab + (bb - ab) * tp);
    }

    private static int scaleAlpha(int argb, float scale) {
        int a = Math.round((argb >>> 24) * Math.clamp(scale, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 带透明度的加载底色（清屏/淡出填充共用，避免 RGB 散落多处）。 */
    private static int bgWithAlpha(float alpha) {
        return ARGB.color(Math.round(Math.clamp(alpha, 0f, 1f) * 255f),
                (BG >> 16) & 0xFF, (BG >> 8) & 0xFF, BG & 0xFF);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOutCubic(float t) {
        float x = Math.clamp(t, 0f, 1f);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3.0) / 2f;
    }
}
