package geminiclient.gemini.modules.impl.visual.effectDisplay;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.modules.impl.visual.EffectDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Material" EffectDisplay renderer — Material Design 3 (MD3) light-theme
 * cards, styled after the Notification module's "Mellow" mode.
 *
 * <h3>MD3 tokens (see also the module javadoc / delivery notes)</h3>
 * <ul>
 *   <li><b>Surface container</b> {@code #F5F4F8} — near-white card surface
 *       (Mellow card colour; ≈ MD3 surface-container-low adapted to the
 *       client's neutral palette).</li>
 *   <li><b>On-surface</b> {@code #26242E} — title text (Mellow title;
 *       softened variant of MD3 on-surface {@code #1D1B20}).</li>
 *   <li><b>Dynamic accent</b> — per-effect colour taken from the potion
 *       effect itself (MD3 dynamic colour); tonal-derived into a light
 *       icon container and a readable dark text tone. Falls back to the
 *       MD3 baseline primary {@code #6750A4} when Dynamic Color is off.</li>
 *   <li><b>Elevation level 1</b> — GLSL rounded-box penumbra
 *       ({@link Md3ShadowRenderer}): key-light offset y+2, sigma 5 px,
 *       peak opacity 16 % (identical to Mellow's shadow tuning).</li>
 *   <li><b>Shape</b> — card corner radius 12 (MD3 "medium" shape scale),
 *       icon container fully round, progress indicator fully rounded.</li>
 *   <li><b>Type</b> — Google Sans (MSDF pipeline): title 9 bold
 *       (≈ MD3 title-small), duration 8 regular (≈ MD3 label-medium).</li>
 * </ul>
 *
 * Motion mirrors the Classic renderer's stack behaviour (same
 * {@link SmoothAnimationTimer} parameters) with an added MD3-style fade.
 */
public final class MaterialEffectRenderer {

    private MaterialEffectRenderer() {}

    // ========================
    //  MD3 colour tokens
    // ========================

    /** MD3 baseline primary — used when "Dynamic Color" is disabled. */
    private static final int MD3_PRIMARY = 0xFF6750A4;

    /** Amount of accent mixed into the surface for the tonal icon disc. */
    private static final float DISC_TONE = 0.80f;
    /** Darkening of the accent for readable duration text on the surface. */
    private static final float TEXT_TONE = 0.30f;
    /** Darkening of the surface for the progress track. */
    private static final float TRACK_TONE = 0.10f;

    // ========================
    //  Layout (GUI px)
    // ========================

    private static final int   CARD_HEIGHT    = 44;
    private static final int   SPACING_Y      = CARD_HEIGHT + 7;
    private static final float PADDING_X      = 12.0F; // anchor ↔ stack gap (Classic parity)
    private static final int   PAD_LEFT       = 10;
    private static final int   DISC_SIZE      = 26;   // tonal icon container
    private static final int   SPRITE_SIZE    = 16;   // potion sprite inside the disc
    private static final int   TEXT_GAP       = 8;
    private static final int   TIME_GAP       = 8;
    private static final int   TEXT_RIGHT_PAD = 10;
    private static final int   MIN_CARD_WIDTH = 132;
    private static final int   PILL_PAD_X     = 7;
    private static final int   PILL_HEIGHT    = 16;
    private static final int   PROGRESS_H     = 3;    // MD3 linear progress indicator
    private static final int   PROGRESS_INSET = 12;
    private static final int   PROGRESS_BOT   = 6;    // card bottom → indicator gap

    // ========================
    //  Indeterminate progress (infinite-duration effects)
    // ========================

    private static final long  INDET_PERIOD_MS = 1400L; // MD3 indeterminate cycle
    private static final float INDET_SEGMENT   = 0.40f; // segment length, fraction of track

    // ========================
    //  Google Sans (shares CustomFontRenderer.FONT_CACHE with Mellow)
    // ========================

    private static final Identifier GOOGLE_SANS =
            Identifier.fromNamespaceAndPath("gemini", "font/googlesans-regular.ttf");
    private static final float TITLE_SIZE    = 9f;
    private static final float DURATION_SIZE = 8f;

    private static volatile CustomFontRenderer.GlyphFont titleFont;
    private static volatile CustomFontRenderer.GlyphFont durationFont;
    private static volatile boolean fontLoadFailed;

    private static CustomFontRenderer.@Nullable GlyphFont titleFont() {
        if (fontLoadFailed) return null;
        CustomFontRenderer.GlyphFont f = titleFont;
        if (f == null) {
            try {
                f = titleFont = CustomFontRenderer.loadFont(
                        GOOGLE_SANS, TITLE_SIZE, java.awt.Font.BOLD);
            } catch (Exception e) {
                fontLoadFailed = true;
                return null;
            }
        }
        return f;
    }

    private static CustomFontRenderer.@Nullable GlyphFont durationFont() {
        if (fontLoadFailed) return null;
        CustomFontRenderer.GlyphFont f = durationFont;
        if (f == null) {
            try {
                f = durationFont = CustomFontRenderer.loadFont(GOOGLE_SANS, DURATION_SIZE);
            } catch (Exception e) {
                fontLoadFailed = true;
                return null;
            }
        }
        return f;
    }

    // ========================
    //  Per-effect animation state
    // ========================

    private static final class MaterialEffectInfo {
        final SmoothAnimationTimer xTimer        = new SmoothAnimationTimer(0.0F, 0.15F);
        final SmoothAnimationTimer yTimer        = new SmoothAnimationTimer(0.0F, 0.15F);
        final SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(0.0F, 0.15F);
        final SmoothAnimationTimer alphaTimer    = new SmoothAnimationTimer(0.0F, 0.15F);

        int duration = 0;
        int maxDuration = 1;
        int amplifier = 0;
        boolean infinite = false;
        float width = 0.0F;
        int color = 0xFFFF5555;
        boolean shouldDisappear = false;
    }

    private final Map<Holder<MobEffect>, MaterialEffectInfo> infos = new ConcurrentHashMap<>();
    private static final MaterialEffectRenderer INSTANCE = new MaterialEffectRenderer();

    public static MaterialEffectRenderer getInstance() {
        return INSTANCE;
    }

    // ========================
    //  Measurement
    // ========================

    private static float nameWidth(String text) {
        CustomFontRenderer.GlyphFont f = titleFont();
        Minecraft mc = Minecraft.getInstance();
        return f != null ? CustomFontRenderer.stringWidth(f, text) : mc.font.width(text);
    }

    private static float durationWidth(String text) {
        CustomFontRenderer.GlyphFont f = durationFont();
        Minecraft mc = Minecraft.getInstance();
        return f != null ? CustomFontRenderer.stringWidth(f, text) : mc.font.width(text);
    }

    private static float titleLineHeight() {
        CustomFontRenderer.GlyphFont f = titleFont();
        return f != null ? f.lineHeight : Minecraft.getInstance().font.lineHeight;
    }

    private static float durationLineHeight() {
        CustomFontRenderer.GlyphFont f = durationFont();
        return f != null ? f.lineHeight : Minecraft.getInstance().font.lineHeight;
    }

    /** Full card width for the given effect name / duration strings. */
    public static float cardWidth(String name, String duration) {
        float pillW = durationPillWidth(duration);
        float w = PAD_LEFT + DISC_SIZE + TEXT_GAP + nameWidth(name)
                + TIME_GAP + pillW + TEXT_RIGHT_PAD;
        return Math.max(w, MIN_CARD_WIDTH);
    }

    // ========================
    //  Main render
    // ========================

    public void render(GuiGraphicsExtractor graphics, EffectDisplay module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        int screenW = mc.getWindow().getGuiScaledWidth();

        // ---- 1. Sync state with active effects ----
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            MaterialEffectInfo info = this.infos.computeIfAbsent(
                    effectHolder, k -> new MaterialEffectInfo());

            info.infinite = effect.isInfiniteDuration();
            if (!info.infinite) {
                info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            }
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;
            info.color = effect.getEffect().value().getColor() | 0xFF000000;

            String name = getDisplayName(effectHolder.value(), info).getString();
            info.width = cardWidth(name, durationText(info));

            info.durationTimer.target = info.maxDuration > 0
                    ? (float) info.duration / (float) info.maxDuration : 0.0F;
            info.xTimer.target = PADDING_X;
            info.alphaTimer.target = 1.0F;
        }

        final float initialY = module.hudY;
        final float[] currentY = { initialY };
        final float[] maxWidth = { 0f };

        // ---- 2. Draw each card (and retire slid-out entries) ----
        this.infos.entrySet().removeIf(entry -> {
            Holder<MobEffect> effect = entry.getKey();
            MaterialEffectInfo info = entry.getValue();

            // MD3 motion: exiting cards slide off the NEAREST screen edge
            // (short, ~200 ms slide + fade) instead of Classic's far target,
            // which made cards pop out within a couple of frames.
            float exitTarget = rightAligned
                    ? originX - info.width - screenW - 24
                    : -(originX + info.width + 24);

            if (!info.shouldDisappear) {
                info.yTimer.target = currentY[0];
            } else {
                info.xTimer.target = exitTarget;
                info.alphaTimer.target = 0.0F;
            }

            info.durationTimer.update(true);
            info.xTimer.update(true);
            info.yTimer.update(true);
            info.alphaTimer.update(true);

            if (!info.shouldDisappear && !mc.player.hasEffect(effect)) {
                info.shouldDisappear = true;
                info.xTimer.target = exitTarget;
                info.alphaTimer.target = 0.0F;
            }

            float x = rightAligned
                    ? originX - info.width - info.xTimer.value
                    : originX + info.xTimer.value;
            float y = info.yTimer.value;
            float alpha = info.alphaTimer.value;

            boolean visible = rightAligned ? (x + info.width > 0) : (x < screenW);

            if (visible) {
                if (alpha > 0.004f) {
                    drawCard(graphics, module, effect, info, x, y, alpha);
                }
                currentY[0] += SPACING_Y;
            }

            if (!info.shouldDisappear) {
                maxWidth[0] = Math.max(maxWidth[0], info.width);
            }

            if (info.shouldDisappear) {
                // Retire once fully past the edge (small margin for the lerp tail).
                return info.xTimer.value <= exitTarget + 8.0F;
            }
            return false;
        });

        // ---- 3. Drag region ----
        float totalH = currentY[0] - initialY;
        if (totalH > 0 && maxWidth[0] > 0) {
            float regionX = rightAligned ? originX - maxWidth[0] - PADDING_X : originX;
            float regionW = maxWidth[0] + PADDING_X * 2;
            Gemini.hudDragManager.registerDragRegion(
                    module, (int) regionX, (int) initialY, (int) regionW, (int) totalH);
        }
    }

    // ========================
    //  Card drawing
    // ========================

    private static void drawCard(GuiGraphicsExtractor gui, EffectDisplay module,
                                 Holder<MobEffect> effect, MaterialEffectInfo info,
                                 float x, float y, float alpha) {
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) Math.ceil(info.width);
        int radius = Math.min(module.cardRadius.getValue(), CARD_HEIGHT / 2);

        int surface = module.surfaceColor.getColor();
        int accent = module.dynamicColor.enabled ? info.color : MD3_PRIMARY;

        // ---- 1. MD3 elevation shadow (GLSL rounded-box penumbra) ----
        if (module.cardShadow.enabled) {
            Md3ShadowRenderer.drawElevationShadow(gui, ix, iy, iw, CARD_HEIGHT,
                    radius, Md3ShadowRenderer.MAX_STRENGTH * alpha);
        }

        // ---- 2. Card surface ----
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix, iy, iw, CARD_HEIGHT,
                radius, scaleAlpha(surface, alpha));
        CustomRoundedRectRenderer.drawRoundedOutline(gui, ix, iy, iw, CARD_HEIGHT,
                radius, scaleAlpha(mixRgb(surface, 0xFF000000, 0.12f), alpha), 1);
        CustomRoundedRectRenderer.drawRoundedRect(gui, ix + radius, iy + 1,
                Math.max(0, iw - radius * 2), 1, 1,
                scaleAlpha(0x66FFFFFF, alpha));

        // ---- 3. Tonal icon container + potion sprite ----
        int disc = scaleAlpha(withAlpha(mixRgb(accent, surface, DISC_TONE)), alpha);
        int discX = ix + PAD_LEFT;

        // ---- 4. Icon + title + duration pill (Google Sans, MSDF pipeline) ----
        float textX = ix + PAD_LEFT + DISC_SIZE + TEXT_GAP;
        float titleH = titleLineHeight();
        String name = getDisplayName(effect.value(), info).getString();
        String duration = durationText(info);
        int pillW = (int) Math.ceil(durationPillWidth(duration));
        int pillX = ix + iw - TEXT_RIGHT_PAD - pillW;

        // Centre the icon, title and time pill as one row above the progress bar.
        float availableH = CARD_HEIGHT - PROGRESS_BOT - PROGRESS_H;
        float contentH = Math.max(DISC_SIZE, Math.max(titleH, PILL_HEIGHT));
        float contentTop = iy + (availableH - contentH) / 2f;
        float discY = contentTop + (contentH - DISC_SIZE) / 2f;
        float titleY = contentTop + (contentH - titleH) / 2f - 0.5f;
        int pillY = (int) (contentTop + (contentH - PILL_HEIGHT) / 2f);

        CustomRoundedRectRenderer.drawRoundedRect(gui, discX, (int) discY,
                DISC_SIZE, DISC_SIZE, DISC_SIZE / 2, disc);
        if (effect.getKey() != null) {
            int a8 = (int) (alpha * 255);
            gui.blitSprite(RenderPipelines.GUI_TEXTURED,
                    Gui.getMobEffectSprite(effect),
                    discX + (DISC_SIZE - SPRITE_SIZE) / 2,
                    (int) discY + (DISC_SIZE - SPRITE_SIZE) / 2,
                    SPRITE_SIZE, SPRITE_SIZE, (a8 << 24) | 0xFFFFFF);
        }

        drawText(gui, true, name, textX, titleY,
                scaleAlpha(module.titleColor.getColor(), alpha));

        int pillBg = scaleAlpha(withAlpha(mixRgb(accent, surface, 0.72f)), alpha);
        CustomRoundedRectRenderer.drawRoundedRect(gui, pillX, pillY,
                pillW, PILL_HEIGHT, PILL_HEIGHT / 2, pillBg);
        float durationX = pillX + (pillW - durationWidth(duration)) / 2f;
        float durationY = pillY + (PILL_HEIGHT - durationLineHeight()) / 2f - 0.5f;
        drawText(gui, false, duration, durationX, durationY,
                scaleAlpha(withAlpha(mixRgb(accent, 0xFF000000, TEXT_TONE)), alpha));

        // ---- 5. MD3 linear progress indicator ----
        if (module.progressBar.enabled) {
            int trackX = ix + PROGRESS_INSET;
            int trackW = iw - PROGRESS_INSET * 2;
            int trackY = iy + CARD_HEIGHT - PROGRESS_BOT - PROGRESS_H;
            int track = scaleAlpha(withAlpha(mixRgb(surface, 0xFF000000, TRACK_TONE)), alpha);
            CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, trackY,
                    trackW, PROGRESS_H, PROGRESS_H / 2, track);

            int active = scaleAlpha(accent, alpha);
            if (info.infinite) {
                drawIndeterminate(gui, trackX, trackY, trackW, active);
            } else if (info.durationTimer.value > 0) {
                int fillW = (int) Math.min(info.durationTimer.value * trackW, trackW);
                if (fillW > 0) {
                    CustomRoundedRectRenderer.drawRoundedRect(gui, trackX, trackY,
                            fillW, PROGRESS_H, PROGRESS_H / 2, active);
                }
            }
        }
    }

    /**
     * MD3 indeterminate linear progress: a segment sweeping the track with
     * emphasized easing, used for infinite-duration (e.g. beacon) effects.
     */
    private static void drawIndeterminate(GuiGraphicsExtractor gui,
                                          int trackX, int trackY, int trackW, int color) {
        float t = (System.currentTimeMillis() % INDET_PERIOD_MS) / (float) INDET_PERIOD_MS;
        float eased = easeInOutCubic(t);
        int segW = Math.max(PROGRESS_H * 2, (int) (trackW * INDET_SEGMENT));
        int segX = trackX + (int) (eased * (trackW + segW)) - segW;

        // Clip the segment to the track bounds.
        int x0 = Math.max(segX, trackX);
        int x1 = Math.min(segX + segW, trackX + trackW);
        if (x1 > x0) {
            CustomRoundedRectRenderer.drawRoundedRect(gui, x0, trackY,
                    x1 - x0, PROGRESS_H, PROGRESS_H / 2, color);
        }
    }

    private static float easeInOutCubic(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    // ========================
    //  Editor placeholder / outline (drag-editor mode)
    // ========================

    /** Gold outline only — shown for the enabled module in the drag editor. */
    public void renderOutline(GuiGraphicsExtractor graphics, EffectDisplay module) {
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        float totalW = cardWidth("Speed II", "12:34") + PADDING_X * 2;
        float x = rightAligned ? originX - totalW : originX;
        int radius = Math.min(module.cardRadius.getValue(), CARD_HEIGHT / 2);

        CustomRoundedRectRenderer.drawRoundedOutline(graphics,
                (int) x, (int) originY, (int) totalW, CARD_HEIGHT,
                radius, 0xAAFFD700, 2);
        Gemini.hudDragManager.registerDragRegion(
                module, (int) x, (int) originY, (int) totalW, CARD_HEIGHT);
    }

    /** Dummy MD3 card — previews the style in the drag editor while disabled. */
    public void renderPlaceholder(GuiGraphicsExtractor graphics, EffectDisplay module) {
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        float cardW = cardWidth("Speed II", "12:34");
        float totalW = cardW + PADDING_X * 2;
        float x = rightAligned ? originX - totalW : originX;
        int ix = (int) x + (int) PADDING_X;
        int iy = (int) originY;
        int radius = Math.min(module.cardRadius.getValue(), CARD_HEIGHT / 2);

        int surface = module.surfaceColor.getColor();
        int accent = module.dynamicColor.enabled ? 0xFF7E74D8 : MD3_PRIMARY; // Mellow purple preview

        // Editor outline around the whole drag region
        CustomRoundedRectRenderer.drawRoundedOutline(graphics,
                (int) x, iy, (int) totalW, CARD_HEIGHT, radius, 0x80FFD700, 2);

        // Shadow + surface
        if (module.cardShadow.enabled) {
            Md3ShadowRenderer.drawElevationShadow(graphics, ix, iy,
                    (int) cardW, CARD_HEIGHT, radius, Md3ShadowRenderer.MAX_STRENGTH);
        }
        CustomRoundedRectRenderer.drawRoundedRect(graphics, ix, iy,
                (int) cardW, CARD_HEIGHT, radius, surface);
        CustomRoundedRectRenderer.drawRoundedOutline(graphics, ix, iy, (int) cardW, CARD_HEIGHT,
                radius, mixRgb(surface, 0xFF000000, 0.12f), 1);
        CustomRoundedRectRenderer.drawRoundedRect(graphics, ix + radius, iy + 1,
                Math.max(0, (int) cardW - radius * 2), 1, 1, 0x66FFFFFF);

        // Tonal disc (empty — no live sprite in the editor)
        int discX = ix + PAD_LEFT;

        // Dummy text
        float textX = ix + PAD_LEFT + DISC_SIZE + TEXT_GAP;
        float titleH = titleLineHeight();
        String duration = "12:34";
        int pillW = (int) Math.ceil(durationPillWidth(duration));
        int pillX = ix + (int) cardW - TEXT_RIGHT_PAD - pillW;

        // Centre the icon, title and time pill as one row above the progress bar.
        float availableH = CARD_HEIGHT - PROGRESS_BOT - PROGRESS_H;
        float contentH = Math.max(DISC_SIZE, Math.max(titleH, PILL_HEIGHT));
        float contentTop = iy + (availableH - contentH) / 2f;
        int discY = (int) (contentTop + (contentH - DISC_SIZE) / 2f);
        float titleY = contentTop + (contentH - titleH) / 2f - 0.5f;
        int pillY = (int) (contentTop + (contentH - PILL_HEIGHT) / 2f);

        CustomRoundedRectRenderer.drawRoundedRect(graphics, discX, discY,
                DISC_SIZE, DISC_SIZE, DISC_SIZE / 2,
                withAlpha(mixRgb(accent, surface, DISC_TONE)));
        drawText(graphics, true, "Speed II", textX, titleY, module.titleColor.getColor());

        CustomRoundedRectRenderer.drawRoundedRect(graphics, pillX, pillY,
                pillW, PILL_HEIGHT, PILL_HEIGHT / 2,
                withAlpha(mixRgb(accent, surface, 0.72f)));
        drawText(graphics, false, duration,
                pillX + (pillW - durationWidth(duration)) / 2f,
                pillY + (PILL_HEIGHT - durationLineHeight()) / 2f - 0.5f,
                withAlpha(mixRgb(accent, 0xFF000000, TEXT_TONE)));

        // Dummy progress at 60 %
        if (module.progressBar.enabled) {
            int trackX = ix + PROGRESS_INSET;
            int trackW = (int) cardW - PROGRESS_INSET * 2;
            int trackY = iy + CARD_HEIGHT - PROGRESS_BOT - PROGRESS_H;
            CustomRoundedRectRenderer.drawRoundedRect(graphics, trackX, trackY,
                    trackW, PROGRESS_H, PROGRESS_H / 2,
                    withAlpha(mixRgb(surface, 0xFF000000, TRACK_TONE)));
            CustomRoundedRectRenderer.drawRoundedRect(graphics, trackX, trackY,
                    (int) (trackW * 0.6f), PROGRESS_H, PROGRESS_H / 2, accent);
        }

        Gemini.hudDragManager.registerDragRegion(
                module, (int) x, iy, (int) totalW, CARD_HEIGHT);
    }

    // ========================
    //  Text & colour helpers
    // ========================

    /** Google Sans text; falls back to the vanilla font (bold title) if the TTF failed. */
    private static void drawText(GuiGraphicsExtractor gui, boolean title, String text,
                                 float x, float y, int argb) {
        if (text == null || text.isEmpty() || (argb >>> 24) == 0) return;
        CustomFontRenderer.GlyphFont f = title ? titleFont() : durationFont();
        if (f != null) {
            CustomFontRenderer.drawString(gui, f, text, x, y, argb);
        } else if (title) {
            gui.text(Minecraft.getInstance().font,
                    Component.literal(text).withStyle(s -> s.withBold(true)),
                    (int) x, (int) y, argb, false);
        } else {
            gui.text(Minecraft.getInstance().font, text, (int) x, (int) y, argb, false);
        }
    }

    private static String durationText(MaterialEffectInfo info) {
        return info.infinite ? "∞" : StringUtil.formatTickDuration(info.duration, 20);
    }

    private static float durationPillWidth(String text) {
        return durationWidth(text) + PILL_PAD_X * 2;
    }

    private static Component getDisplayName(MobEffect effect, MaterialEffectInfo info) {
        MutableComponent name = effect.getDisplayName().copy();
        if (info.amplifier >= 1) {
            name.append(" ").append(Component.translatable(
                    "enchantment.level." + (info.amplifier + 1)));
        }
        return name;
    }

    /** Multiplies the alpha channel of an ARGB colour. */
    private static int scaleAlpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * mul)));
        return (argb & 0xFFFFFF) | (a << 24);
    }

    /** Forces full alpha on an RGB colour. */
    private static int withAlpha(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Linear RGB mix of two colours; alpha of the result is taken from {@code argbA}. */
    private static int mixRgb(int argbA, int argbB, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((argbA >> 16) & 0xFF) * (1f - t) + ((argbB >> 16) & 0xFF) * t);
        int g = (int) (((argbA >> 8)  & 0xFF) * (1f - t) + ((argbB >> 8)  & 0xFF) * t);
        int b = (int) (((argbA)       & 0xFF) * (1f - t) + ((argbB)       & 0xFF) * t);
        return (argbA & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
