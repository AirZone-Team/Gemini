package geminiclient.gemini.base;

import com.mojang.blaze3d.platform.NativeImage;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRectRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer.GlyphFont;
import geminiclient.gemini.customRenderer.glsl.InfiniteGridRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static geminiclient.gemini.base.MenuUI.*;

/**
 * 背景选择器 —— 主菜单壁纸的磨砂网格画廊（单色设计语言）。
 *
 * <p>与主菜单共享同一张壁纸背景（{@link MenuBackdrop}，引用计数保证互切时
 * 资源存活），上面叠一层轻压暗；居中磨砂玻璃面板内排列方形缩略图卡片，
 * 末尾一张"添加"卡。白色即强调色：选中卡片亮白描边 + 右下白点，悬停卡片
 * 轻微上浮并浮现删除钮。选中立即应用——壁纸就在面板背后实时预览，面板不
 * 关闭，可连续试多张。支持滚轮平滑滚动、拖拽导入与原生文件选择器。</p>
 */
public class BackgroundSelectorScreen extends Screen {

    // ========================
    // Layout Constants
    // ========================
    private static final float PANEL_PAD   = 20f;
    private static final float HEADER_H    = 64f;
    private static final float FOOTER_H    = 52f;
    private static final float PANEL_MAX_W = 640f;
    private static final float PANEL_MAX_H = 460f;
    private static final int   CARD_SIZE   = 128;
    private static final int   CARD_GAP    = 14;
    private static final int   GRID_MAX_COLUMNS = 4;
    private static final float NAME_GAP    = 5f;
    private static final float WHEEL_STEP  = 30f;

    private static final float CLOSE_BTN_D   = 32f;
    private static final float DELETE_BTN_D  = 22f;
    private static final float TOGGLE_BTN_H  = 30f;

    // 缩略图纹理分辨率与卡片 1:1，圆角直接烘焙进纹理
    private static final int THUMBNAIL_SIZE = CARD_SIZE;
    private static final int THUMB_RADIUS   = 20;

    // ========================
    // Fonts
    // ========================
    // 全部面取自 MiSans-Bold：资源中唯一含完整中文字形的 MiSans 文件。
    private static final Identifier FONT_REGULAR =
            Identifier.fromNamespaceAndPath("gemini", "font/misans-bold.ttf");

    private static final float TITLE_FONT_SIZE = 24f;
    private static final float BODY_FONT_SIZE  = 12f;
    private static final float TINY_FONT_SIZE  = 10f;

    private static GlyphFont titleFont; // 面板标题
    private static GlyphFont bodyFont;  // 按钮 / 图标
    private static GlyphFont tinyFont;  // 文件名 / 提示

    private static void ensureFontsLoaded() {
        if (titleFont == null) titleFont = CustomFontRenderer.loadFont(FONT_REGULAR, TITLE_FONT_SIZE);
        if (bodyFont == null)  bodyFont  = CustomFontRenderer.loadFont(FONT_REGULAR, BODY_FONT_SIZE);
        if (tinyFont == null)  tinyFont  = CustomFontRenderer.loadFont(FONT_REGULAR, TINY_FONT_SIZE);
    }

    /**
     * 预热背景选择器字体，由 {@code UiShaderWarmup} 在加载界面调用。
     * 调用方需随后执行 {@code CustomFontRenderer.flushPendingGlyphs()}。
     */
    public static void warmup() {
        try {
            ensureFontsLoaded();
            String cjk = "背景添加关闭启用动态选择壁纸拖入文件可";
            String symbols = "×▶·";
            for (GlyphFont face : new GlyphFont[] {titleFont, bodyFont, tinyFont}) {
                if (face == null) {
                    continue;
                }
                for (int cp = 0x20; cp <= 0x7E; cp++) {
                    face.getGlyphBlocking(cp);
                }
                for (int i = 0; i < cjk.length(); ) {
                    int cp = cjk.codePointAt(i);
                    face.getGlyphBlocking(cp);
                    i += Character.charCount(cp);
                }
                for (int i = 0; i < symbols.length(); i++) {
                    face.getGlyphBlocking(symbols.charAt(i));
                }
            }
        } catch (Throwable t) {
            // 预热失败不影响运行：字形仍会惰性三角化。
        }
    }

    // ========================
    // Animation Constants
    // ========================
    private static final float HOVER_SPEED      = 12f;
    private static final float ENTRY_FADE_SPEED = 4f;
    private static final float ENTRY_STAGGER    = 0.035f;
    private static final float SCROLL_SPEED     = 14f;
    private static final float PANEL_RISE_PX    = 12f;
    private static final float CELL_SLIDE_PX    = 14f;

    // ========================
    // Inner Types
    // ========================

    /** 单一权威面板布局，绘制与命中测试共享。 */
    private record Layout(
            float px, float py, float pw, float ph,
            float gridX, float gridY, float gridW, float gridH,
            int columns, float cellH,
            float closeCx, float closeCy,
            float toggleX, float toggleY, float toggleW, float toggleH) {}

    // ========================
    // State
    // ========================

    private final Screen parent;
    private final FileSystem fileSystem;
    private final List<WallpaperEntry> wallpapers = new ArrayList<>();
    private final Map<Path, Identifier> thumbnailCache = new HashMap<>();

    private float entryAlpha;
    private long screenOpenTime;
    private long lastFrameMs;
    private boolean firstInit = true;

    private float scrollOffset;
    private float targetScroll;

    private float[] cellHover = new float[0]; // 每格悬停进度，末格 = 添加卡
    private int hoveredCell = -1;
    private int selectedIndex = -1;

    private float closeHover;
    private float toggleHover;
    private float deleteAnim;
    private int deleteCell = -1;

    private boolean thumbnailsPreloaded;

    // ========================
    // Constructor
    // ========================

    public BackgroundSelectorScreen(Screen parent) {
        super(Component.literal("Background Selector"));
        this.parent = parent;
        this.fileSystem = Gemini.fileSystem;
        // 引用计数：主菜单 ↔ 选择器互切时壁纸资源保持存活
        MenuBackdrop.acquire();
    }

    // ========================
    // Lifecycle
    // ========================

    @Override
    protected void init() {
        super.init();

        if (firstInit) {
            screenOpenTime = System.currentTimeMillis();
            entryAlpha = 0f;
            firstInit = false;
        }
        lastFrameMs = System.currentTimeMillis();

        scanWallpapers();
        preloadThumbnails();
        updateSelectedIndex();
    }

    private void scanWallpapers() {
        wallpapers.clear();
        if (fileSystem != null) {
            wallpapers.addAll(fileSystem.scanWallpaperEntries());
        }
    }

    /** 后台线程预生成全部静态壁纸缩略图，避免打开时逐张卡顿。 */
    private void preloadThumbnails() {
        if (thumbnailsPreloaded) {
            return;
        }
        thumbnailsPreloaded = true;
        Thread preloader = new Thread(() -> {
            for (WallpaperEntry entry : wallpapers) {
                if (!entry.isAnimated() && !thumbnailCache.containsKey(entry.filePath())) {
                    generateThumbnail(entry);
                }
            }
        });
        preloader.setDaemon(true);
        preloader.setName("ThumbnailPreloader");
        preloader.start();
    }

    private void updateSelectedIndex() {
        selectedIndex = -1;
        if (fileSystem == null) {
            return;
        }
        Path currentBg = fileSystem.getCustomBackgroundFile();
        for (int i = 0; i < wallpapers.size(); i++) {
            if (wallpapers.get(i).filePath().equals(currentBg)) {
                selectedIndex = i;
                break;
            }
        }
    }

    @Override
    public void onClose() {
        MenuBackdrop.reload();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        super.removed();
        for (Identifier id : thumbnailCache.values()) {
            AbstractTexture texture = this.minecraft.getTextureManager().getTexture(id);
            if (texture != null) {
                texture.close();
            }
            this.minecraft.getTextureManager().release(id);
        }
        thumbnailCache.clear();
        // 引用计数归零（进入世界 / 打开其他界面）时才真正释放壁纸；
        // 主菜单 ↔ 选择器互切时资源保持存活。
        MenuBackdrop.release();
    }

    // ========================
    // Thumbnail Generation
    // ========================

    private void generateThumbnail(WallpaperEntry entry) {
        try {
            BufferedImage original = ImageIO.read(entry.filePath().toFile());
            if (original == null) {
                return;
            }

            int size = THUMBNAIL_SIZE;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = scaled.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            // 居中裁方
            int srcSize = Math.min(original.getWidth(), original.getHeight());
            int srcX = (original.getWidth() - srcSize) / 2;
            int srcY = (original.getHeight() - srcSize) / 2;
            g2d.drawImage(original, 0, 0, size, size, srcX, srcY, srcX + srcSize, srcY + srcSize, null);
            g2d.dispose();

            applyRoundedCorners(scaled, THUMB_RADIUS);

            NativeImage nativeImg = new NativeImage(size, size, true);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    nativeImg.setPixel(x, y, scaled.getRGB(x, y));
                }
            }

            final NativeImage img = nativeImg;
            final Path path = entry.filePath();
            this.minecraft.execute(() -> {
                try {
                    DynamicTexture texture = new DynamicTexture(() -> "thumbnail_" +
                            path.getFileName().toString().hashCode(), img);
                    Identifier id = Identifier.fromNamespaceAndPath("gemini", "thumbnail_" +
                            path.getFileName().toString().hashCode());
                    this.minecraft.getTextureManager().register(id, texture);
                    thumbnailCache.put(path, id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception ignored) {
            // 缩略图生成失败时显示玻璃占位块
        }
    }

    private void applyRoundedCorners(BufferedImage image, int radius) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean inCorner = false;
                int cornerCenterX = 0;
                int cornerCenterY = 0;

                if (x < radius && y < radius) {
                    inCorner = true;
                    cornerCenterX = radius;
                    cornerCenterY = radius;
                } else if (x >= width - radius && y < radius) {
                    inCorner = true;
                    cornerCenterX = width - radius - 1;
                    cornerCenterY = radius;
                } else if (x < radius && y >= height - radius) {
                    inCorner = true;
                    cornerCenterX = radius;
                    cornerCenterY = height - radius - 1;
                } else if (x >= width - radius && y >= height - radius) {
                    inCorner = true;
                    cornerCenterX = width - radius - 1;
                    cornerCenterY = height - radius - 1;
                }

                if (inCorner) {
                    double distance = Math.sqrt(
                            Math.pow(x - cornerCenterX, 2) + Math.pow(y - cornerCenterY, 2));
                    if (distance > radius) {
                        image.setRGB(x, y, 0x00000000);
                    }
                }
            }
        }
    }

    private void releaseThumbnail(Path path) {
        Identifier id = thumbnailCache.remove(path);
        if (id == null) {
            return;
        }
        AbstractTexture texture = this.minecraft.getTextureManager().getTexture(id);
        if (texture != null) {
            texture.close();
        }
        this.minecraft.getTextureManager().release(id);
    }

    // ========================
    // Layout
    // ========================

    private Layout layout() {
        ensureFontsLoaded();
        float availGridW = Math.min(PANEL_MAX_W, this.width - 48f) - PANEL_PAD * 2f;
        int columns = Math.max(1, Math.min(GRID_MAX_COLUMNS,
                (int) ((availGridW + CARD_GAP) / (CARD_SIZE + CARD_GAP))));
        float pw = PANEL_PAD * 2f + columns * CARD_SIZE + (columns - 1f) * CARD_GAP;
        float ph = Math.max(HEADER_H + FOOTER_H + CARD_SIZE + 40f,
                Math.min(PANEL_MAX_H, this.height - 72f));
        float px = (this.width - pw) / 2f;
        float py = (this.height - ph) / 2f;
        float gridX = px + PANEL_PAD;
        float gridY = py + HEADER_H;
        float gridW = pw - PANEL_PAD * 2f;
        float gridH = ph - HEADER_H - FOOTER_H;
        float cellH = CARD_SIZE + NAME_GAP + (tinyFont != null ? tinyFont.lineHeight : 13f);
        float closeCx = px + pw - PANEL_PAD - CLOSE_BTN_D / 2f;
        float closeCy = py + HEADER_H / 2f;
        float toggleW = Math.max(buttonWidth("启用壁纸"), buttonWidth("关闭壁纸"));
        float toggleX = px + pw - PANEL_PAD - toggleW;
        float toggleY = py + ph - FOOTER_H + (FOOTER_H - TOGGLE_BTN_H) / 2f;
        return new Layout(px, py, pw, ph, gridX, gridY, gridW, gridH, columns, cellH,
                closeCx, closeCy, toggleX, toggleY, toggleW, TOGGLE_BTN_H);
    }

    private float buttonWidth(String label) {
        return (bodyFont == null ? 40f : CustomFontRenderer.stringWidth(bodyFont, label)) + 32f;
    }

    private float cellX(Layout l, int index) {
        return l.gridX() + (index % l.columns()) * (CARD_SIZE + CARD_GAP);
    }

    private float cellY(Layout l, int index) {
        return l.gridY() + (index / l.columns()) * (l.cellH() + CARD_GAP) - scrollOffset;
    }

    private float contentHeight(Layout l) {
        int total = wallpapers.size() + 1; // 末格为添加卡
        int rows = (total + l.columns() - 1) / l.columns();
        return rows * l.cellH() + (rows - 1) * CARD_GAP;
    }

    private float maxScroll(Layout l) {
        return Math.max(0f, contentHeight(l) - l.gridH());
    }

    private void clampScroll(Layout l) {
        targetScroll = Math.clamp(targetScroll, 0f, maxScroll(l));
    }

    // ========================
    // Main render
    // ========================

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameMs) / 1000f, 0.1f);
        lastFrameMs = now;
        float elapsed = (now - screenOpenTime) / 1000f;

        entryAlpha += (1f - entryAlpha) * dt * ENTRY_FADE_SPEED;
        if (entryAlpha > 0.99f) entryAlpha = 1f;

        // ── 1. 背景：与主菜单共享的壁纸；无壁纸时回退 GLSL 网格 ──
        MenuBackdrop.render(gui, this.width, this.height, mouseX, mouseY);
        if (!MenuBackdrop.isActive()) {
            InfiniteGridRenderer.render(elapsed);
        }
        // 轻压暗，保证白色文字与玻璃面板在亮壁纸上可读
        CustomRectRenderer.drawRect(gui, 0, 0, this.width, this.height,
                scaleAlpha(0x3D000000, entryAlpha));

        // ── 2. 动画推进 ──────────────────────────────
        Layout l = layout();
        scrollOffset += (targetScroll - scrollOffset) * dt * SCROLL_SPEED;
        updateHovers(l, mouseX, mouseY, dt);

        float reveal = easeOutCubic(clamp01((elapsed - 0.1f) * 2.2f)) * entryAlpha;
        float rise = (1f - reveal) * PANEL_RISE_PX;

        // ── 3. 磨砂面板 + 头部 + 网格 + 页脚 ──────────
        glassPanel(gui, l.px(), l.py() + rise, l.pw(), l.ph(), 16f, reveal, 8f);
        drawHeader(gui, l, elapsed, rise);
        drawGrid(gui, l, elapsed, rise);
        drawFooter(gui, l, elapsed, rise);
    }

    private void drawHeader(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.05f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;
        int alpha = (int) (reveal * 255);

        if (titleFont != null) {
            CustomFontRenderer.drawString(gui, titleFont, I18n.tr("BackGround"),
                    l.px() + PANEL_PAD, l.py() + rise + (HEADER_H - titleFont.lineHeight) / 2f,
                    (alpha << 24) | (TEXT_HIGH & 0x00FFFFFF));
        }
        CustomRectRenderer.drawRect(gui, Math.round(l.px() + PANEL_PAD),
                Math.round(l.py() + rise + HEADER_H), Math.round(l.pw() - PANEL_PAD * 2f), 1,
                scaleAlpha(0x14FFFFFF, reveal));

        ghostIconButton(gui, bodyFont, "×", l.closeCx(), l.closeCy() + rise,
                (int) CLOSE_BTN_D, closeHover, true, reveal);
    }

    private void drawGrid(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        int total = wallpapers.size() + 1;

        gui.enableScissor(Math.max(0, (int) l.gridX() - 2), Math.max(0, (int) l.gridY()),
                Math.min(this.width, (int) (l.gridX() + l.gridW()) + 2),
                Math.min(this.height, (int) (l.gridY() + l.gridH())));

        for (int i = 0; i < total; i++) {
            float cellY = cellY(l, i) + rise;
            if (cellY + l.cellH() < l.gridY() || cellY > l.gridY() + l.gridH()) {
                continue;
            }

            float reveal = easeOutCubic(clamp01((elapsed - 0.25f - i * ENTRY_STAGGER) * 3f)) * entryAlpha;
            if (reveal <= 0.01f) continue;
            float slide = (1f - reveal) * CELL_SLIDE_PX;
            float hp = i < cellHover.length ? cellHover[i] : 0f;

            float x = cellX(l, i);
            float y = cellY + slide;
            if (i < wallpapers.size()) {
                drawWallpaperCard(gui, wallpapers.get(i), x, y, reveal, hp, i == selectedIndex);
            } else {
                drawAddCard(gui, x, y, reveal, hp);
            }
        }

        gui.disableScissor();
        drawScrollbar(gui, l, rise);
    }

    private void drawWallpaperCard(GuiGraphicsExtractor gui, WallpaperEntry entry,
                                   float x, float y, float reveal, float hp, boolean selected) {
        float ty = y - hp * 2f; // 悬停轻微上浮
        int ix = Math.round(x);
        int iy = Math.round(ty);

        Identifier thumb = thumbnailCache.get(entry.filePath());
        if (thumb != null) {
            gui.blit(RenderPipelines.GUI_TEXTURED, thumb, ix, iy, 0, 0,
                    CARD_SIZE, CARD_SIZE, CARD_SIZE, CARD_SIZE, scaleAlpha(WHITE, reveal));
        } else {
            // 动态壁纸 / 未加载完成：玻璃占位块
            CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, CARD_SIZE, CARD_SIZE, THUMB_RADIUS,
                    scaleAlpha(HOVER_FILL, reveal));
            if (entry.isAnimated() && bodyFont != null) {
                drawCentered(gui, bodyFont, "▶", x + CARD_SIZE / 2f,
                        ty + (CARD_SIZE - bodyFont.lineHeight) / 2f, scaleAlpha(TEXT_BODY, reveal));
            }
        }

        // 描边：默认玻璃描边，悬停提亮，选中亮白加粗
        int outline = selected ? 0xB4FFFFFF : lerpColor(GLASS_OUTLINE, 0x66FFFFFF, hp);
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, CARD_SIZE, CARD_SIZE, THUMB_RADIUS,
                scaleAlpha(outline, reveal), selected ? 2 : 1);

        // 选中白点（右下角，呼应主菜单 BG 按钮的状态点）
        if (selected) {
            CustomRoundedRectRenderer.drawCircle(gui, x + CARD_SIZE - 14f, ty + CARD_SIZE - 14f, 6,
                    scaleAlpha(TEXT_HIGH, reveal));
        }

        // 悬停浮现删除钮，进入按钮时转向语义红
        if (hp > 0.02f && bodyFont != null) {
            float dcx = x + CARD_SIZE - 8f - DELETE_BTN_D / 2f;
            float dcy = ty + 8f + DELETE_BTN_D / 2f;
            float btnAlpha = reveal * hp;
            CustomRoundedRectRenderer.drawCircle(gui, dcx, dcy, (int) DELETE_BTN_D,
                    scaleAlpha(0x96000000, btnAlpha));
            CustomRoundedRectRenderer.drawRing(gui, dcx, dcy, Math.round(DELETE_BTN_D / 2f - 0.5f), 1,
                    scaleAlpha(lerpColor(0x50FFFFFF, ERROR, deleteAnim), btnAlpha));
            drawCentered(gui, bodyFont, "×", dcx, dcy - bodyFont.lineHeight / 2f,
                    scaleAlpha(lerpColor(TEXT_HIGH, ERROR, deleteAnim), btnAlpha));
        }

        if (tinyFont != null) {
            String label = entry.fileName();
            if (entry.isAnimated()) {
                label = label + "  ·  " + I18n.tr("Animated");
            }
            label = ellipsize(tinyFont, label, CARD_SIZE + 10f);
            drawCentered(gui, tinyFont, label, x + CARD_SIZE / 2f, y + CARD_SIZE + NAME_GAP,
                    scaleAlpha(lerpColor(TEXT_BODY, TEXT_HIGH, hp), reveal));
        }
    }

    private void drawAddCard(GuiGraphicsExtractor gui, float x, float y, float reveal, float hp) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, CARD_SIZE, CARD_SIZE, THUMB_RADIUS,
                scaleAlpha(HOVER_FILL, reveal * (0.4f + hp * 0.6f)));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, CARD_SIZE, CARD_SIZE, THUMB_RADIUS,
                scaleAlpha(lerpColor(0x33FFFFFF, 0x66FFFFFF, hp), reveal), 1);

        float cx = x + CARD_SIZE / 2f;
        float cy = y + CARD_SIZE / 2f - 8f;
        int plusColor = scaleAlpha(lerpColor(TEXT_FAINT, TEXT_HIGH, hp), reveal);
        CustomRectRenderer.drawRect(gui, Math.round(cx - 1), Math.round(cy - 10), 2, 20, plusColor);
        CustomRectRenderer.drawRect(gui, Math.round(cx - 10), Math.round(cy - 1), 20, 2, plusColor);

        if (tinyFont != null) {
            drawCentered(gui, tinyFont, I18n.tr("Add BackGround.."), x + CARD_SIZE / 2f,
                    y + CARD_SIZE + NAME_GAP,
                    scaleAlpha(lerpColor(TEXT_FAINT, TEXT_BODY, hp), reveal));
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor gui, Layout l, float rise) {
        float max = maxScroll(l);
        if (max <= 0.5f) return;
        float contentH = contentHeight(l);
        float barH = Math.min(l.gridH(), Math.max(Math.min(24f, l.gridH()), l.gridH() * l.gridH() / contentH));
        float barY = l.gridY() + rise + (l.gridH() - barH) * (scrollOffset / max);
        CustomRectRenderer.drawRect(gui, Math.round(l.gridX() + l.gridW() - 2f), Math.round(barY),
                2, Math.round(barH), scaleAlpha(0x40FFFFFF, entryAlpha));
    }

    private void drawFooter(GuiGraphicsExtractor gui, Layout l, float elapsed, float rise) {
        float reveal = easeOutCubic(clamp01((elapsed - 0.4f) * 2.5f)) * entryAlpha;
        if (reveal <= 0.01f) return;

        CustomRectRenderer.drawRect(gui, Math.round(l.px() + PANEL_PAD),
                Math.round(l.py() + rise + l.ph() - FOOTER_H),
                Math.round(l.pw() - PANEL_PAD * 2f), 1, scaleAlpha(0x14FFFFFF, reveal));

        if (tinyFont != null) {
            CustomFontRenderer.drawString(gui, tinyFont, I18n.tr("拖入文件可添加壁纸"),
                    l.px() + PANEL_PAD,
                    l.py() + rise + l.ph() - FOOTER_H + (FOOTER_H - tinyFont.lineHeight) / 2f,
                    scaleAlpha(TEXT_FAINT, reveal));
        }

        if (bodyFont != null && fileSystem != null) {
            String label = I18n.tr(fileSystem.isCustomBackgroundEnabled() ? "关闭壁纸" : "启用壁纸");
            ghostButton(gui, bodyFont, label, l.toggleX(), l.toggleY() + rise,
                    l.toggleW(), l.toggleH(), toggleHover, reveal);
        }
    }

    // ========================
    // Hover updates
    // ========================

    private void updateHovers(Layout l, double mx, double my, float dt) {
        int total = wallpapers.size() + 1;
        if (cellHover.length < total) {
            cellHover = Arrays.copyOf(cellHover, total);
        }

        hoveredCell = cellAt(l, mx, my);
        for (int i = 0; i < total; i++) {
            float target = i == hoveredCell ? 1f : 0f;
            cellHover[i] += (target - cellHover[i]) * dt * HOVER_SPEED;
        }

        boolean delOver = hoveredCell >= 0 && hoveredCell < wallpapers.size()
                && inDeleteZone(l, mx, my, hoveredCell);
        deleteCell = delOver ? hoveredCell : -1;
        deleteAnim += ((delOver ? 1f : 0f) - deleteAnim) * dt * HOVER_SPEED;

        float closeR = CLOSE_BTN_D / 2f + 2f;
        boolean overClose = inRect(mx, my, l.closeCx() - closeR, l.closeCy() - closeR,
                closeR * 2, closeR * 2);
        closeHover += ((overClose ? 1f : 0f) - closeHover) * dt * HOVER_SPEED;

        boolean overToggle = inRect(mx, my, l.toggleX(), l.toggleY(), l.toggleW(), l.toggleH());
        toggleHover += ((overToggle ? 1f : 0f) - toggleHover) * dt * HOVER_SPEED;
    }

    private int cellAt(Layout l, double mx, double my) {
        int total = wallpapers.size() + 1;
        if (!inRect(mx, my, l.gridX(), l.gridY(), l.gridW(), l.gridH())) {
            return -1;
        }
        int col = (int) ((mx - l.gridX()) / (CARD_SIZE + CARD_GAP));
        if (col < 0 || col >= l.columns()) {
            return -1;
        }
        int row = (int) ((my - l.gridY() + scrollOffset) / (l.cellH() + CARD_GAP));
        if (row < 0) {
            return -1;
        }
        int index = row * l.columns() + col;
        if (index >= total) {
            return -1;
        }
        return inRect(mx, my, cellX(l, index), cellY(l, index), CARD_SIZE, l.cellH()) ? index : -1;
    }

    private boolean inDeleteZone(Layout l, double mx, double my, int index) {
        float cx = cellX(l, index) + CARD_SIZE - 8f - DELETE_BTN_D / 2f;
        float cy = cellY(l, index) + 8f + DELETE_BTN_D / 2f;
        float r = DELETE_BTN_D / 2f + 4f;
        return inRect(mx, my, cx - r, cy - r, r * 2, r * 2);
    }

    // ========================
    // Input
    // ========================

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean idk) {
        double mx = mouse.x();
        double my = mouse.y();
        Layout l = layout();

        float closeR = CLOSE_BTN_D / 2f + 2f;
        if (inRect(mx, my, l.closeCx() - closeR, l.closeCy() - closeR, closeR * 2, closeR * 2)) {
            onClose();
            return true;
        }
        if (inRect(mx, my, l.toggleX(), l.toggleY(), l.toggleW(), l.toggleH())) {
            toggleWallpaper();
            return true;
        }

        if (!inRect(mx, my, l.px(), l.py(), l.pw(), l.ph())) {
            // 点击面板外关闭
            onClose();
            return true;
        }

        int cell = cellAt(l, mx, my);
        if (cell >= 0 && cell < wallpapers.size()) {
            if (inDeleteZone(l, mx, my, cell)) {
                deleteWallpaper(cell);
            } else {
                selectWallpaper(cell);
            }
            return true;
        }
        if (cell == wallpapers.size()) {
            openFileChooser();
            return true;
        }
        return true; // 面板内其他区域吞掉
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        Layout l = layout();
        if (inRect(x, y, l.px(), l.py(), l.pw(), l.ph())) {
            targetScroll -= (float) scrollY * WHEEL_STEP;
            clampScroll(l);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        if (fileSystem == null) {
            return;
        }
        // importFirstWallpaper 内部会自动选中并启用
        if (fileSystem.importFirstWallpaper(files).isPresent()) {
            scanWallpapers();
            updateSelectedIndex();
            clampScroll(layout());
            MenuBackdrop.reload();
        }
    }

    // ========================
    // Actions
    // ========================

    /** 选中即应用：面板背后的壁纸下一帧实时刷新，可连续试多张。 */
    private void selectWallpaper(int index) {
        if (fileSystem == null || index < 0 || index >= wallpapers.size()) {
            return;
        }
        selectedIndex = index;
        fileSystem.setSelectedWallpaper(wallpapers.get(index).filePath());
        fileSystem.setCustomBackgroundEnabled(true);
        MenuBackdrop.reload();
    }

    private void toggleWallpaper() {
        if (fileSystem == null) {
            return;
        }
        fileSystem.toggleCustomBackground();
        if (fileSystem.isCustomBackgroundEnabled()) {
            // 下一次渲染按当前壁纸重建
            MenuBackdrop.reload();
        } else {
            // 关闭后停掉视频解码线程，避免空转
            MenuBackdrop.releaseAll();
        }
    }

    private void deleteWallpaper(int index) {
        if (fileSystem == null || index < 0 || index >= wallpapers.size()) {
            return;
        }
        try {
            WallpaperEntry entry = wallpapers.get(index);
            Path filePath = entry.filePath();
            boolean wasSelected = index == selectedIndex;
            Path currentBg = fileSystem.getCustomBackgroundFile();

            if (!fileSystem.deleteWallpaper(filePath)) {
                return;
            }

            wallpapers.remove(index);
            releaseThumbnail(filePath);

            if (wasSelected || filePath.equals(currentBg)) {
                updateSelectedIndex();
                MenuBackdrop.reload();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFileChooser() {
        if (fileSystem == null) {
            return;
        }
        Thread chooser = new Thread(() -> {
            try {
                String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                        I18n.tr("Select Wallpaper"),
                        System.getProperty("user.home"),
                        null,
                        "Image Files (*.png, *.jpg, *.jpeg, *.gif, *.mp4, *.webm)",
                        false);
                if (selectedPath == null || selectedPath.isEmpty()) {
                    return;
                }
                Path imported = fileSystem.importWallpaper(Path.of(selectedPath)).orElse(null);
                if (imported == null) {
                    return;
                }
                this.minecraft.execute(() -> {
                    // 原地刷新：导入后立即重扫列表并实时预览
                    scanWallpapers();
                    updateSelectedIndex();
                    clampScroll(layout());
                    MenuBackdrop.reload();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        chooser.setDaemon(true);
        chooser.setName("BackgroundSelector-FileChooser");
        chooser.start();
    }
}
