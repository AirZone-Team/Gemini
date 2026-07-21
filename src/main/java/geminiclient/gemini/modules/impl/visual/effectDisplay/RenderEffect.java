package geminiclient.gemini.modules.impl.visual.effectDisplay;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.cpu.CustomRoundedRectRenderer;
import geminiclient.gemini.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenderEffect {

    // --- Acrylic glass palette ---
    private static final int BG_GLASS_TOP = 0xCC0C0C0C;
    private static final int BG_GLASS_BOT = 0xAA050505;
    private static final int GLASS_HIGHLIGHT = 0x14FFFFFF;
    private static final int BORDER_GLOW = 0x10FFFFFF;

    // --- Shadow ---
    private static final int SHADOW_COLOR = 0x000000;
    private static final int SHADOW_LAYERS = 3;
    private static final int SHADOW_SPREAD = 2;

    private static final int DURATION_TEXT_COLOR_RGB = 0xFFAAAAAA;
    private static final int ICON_BG_COLOR = 0x44FFFFFF;

    private static final float PADDING_X = 12.0F;
    private static final float ELEMENT_HEIGHT = 32.0F;
    private static final float SPACING_Y = 38.0F;
    private static final float ICON_SIZE = 18.0F;
    private static final int CORNER_RADIUS = 5;

    private final Map<Holder<MobEffect>, MobEffectInfo> infos = new ConcurrentHashMap<>();
    private static final RenderEffect INSTANCE = new RenderEffect();

    public static RenderEffect getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphicsExtractor graphics, Module module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        int screenW = mc.getWindow().getGuiScaledWidth();

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            MobEffectInfo info = this.infos.computeIfAbsent(effectHolder, k -> new MobEffectInfo());

            info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;
            info.color = effect.getEffect().value().getColor() | 0xFF000000;

            String text = getDisplayName(effectHolder.value(), info).getString();
            info.width = mc.font.width(text) * 0.85F + ICON_SIZE + 40.0F;

            info.durationTimer.target = (float) info.duration / (float) info.maxDuration * info.width;
            info.xTimer.target = PADDING_X;
        }

        final float initialY = module.hudY;
        final float[] currentY = { initialY };
        final float[] maxWidth = { 0f };

        this.infos.entrySet().removeIf(entry -> {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInfo info = entry.getValue();

            if (!info.shouldDisappear) {
                info.yTimer.target = currentY[0];
            } else {
                info.xTimer.target = -info.width - screenW;
            }

            info.durationTimer.update(true);
            info.xTimer.update(true);
            info.yTimer.update(true);

            if (!info.shouldDisappear && !mc.player.hasEffect(effect)) {
                info.shouldDisappear = true;
                info.xTimer.target = -info.width - screenW;
            }

            float x = rightAligned
                    ? originX - info.width - info.xTimer.value
                    : originX + info.xTimer.value;
            float y = info.yTimer.value;

            boolean visible = rightAligned ? (x + info.width > 0) : (x < screenW);

            if (visible) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);

                int w = (int) info.width;
                int h = (int) ELEMENT_HEIGHT;

                // --- Multi-layer shadow ---
                for (int i = 0; i < SHADOW_LAYERS; i++) {
                    int sa = 0x30 - (i * 10);
                    int so = SHADOW_SPREAD + i;
                    CustomRoundedRectRenderer.drawRoundedRect(graphics,
                            so, so, w, h, CORNER_RADIUS + i,
                            (SHADOW_COLOR & 0xFFFFFF) | (sa << 24));
                }

                // --- Glass background ---
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(graphics,
                        0, 0, w, h, CORNER_RADIUS, BG_GLASS_TOP, BG_GLASS_BOT);

                // --- Glass reflection highlight ---
                CustomRoundedRectRenderer.drawRoundedRect(graphics,
                        1, 0, w - 2, 1, CORNER_RADIUS, GLASS_HIGHLIGHT);

                // --- Border glow ---
                CustomRoundedRectRenderer.drawRoundedOutline(graphics,
                        0, 0, w, h, CORNER_RADIUS, BORDER_GLOW, 1);

                // --- Accent bar (left edge) with glow ---
                int accentColor = info.color;
                int accentDark = darken(accentColor, 0.35f);
                int accentGlow = (accentColor & 0x00FFFFFF) | 0x30 << 24;
                CustomRoundedRectRenderer.drawRoundedRect(graphics,
                        -1, 2, 4, h - 4, 1, accentGlow);
                CustomRoundedRectRenderer.drawRoundedRectVertGrad(graphics,
                        0, 3, 2, h - 6, 1, accentColor, accentDark);

                // --- Icon with background ---
                int iconX = 8;
                int iconY = (int) (h / 2 - ICON_SIZE / 2);
                if (effect.getKey() != null) {
                    CustomRoundedRectRenderer.drawRoundedRect(graphics,
                            iconX - 1, iconY - 1,
                            (int) ICON_SIZE + 2, (int) ICON_SIZE + 2,
                            3, ICON_BG_COLOR);
                    graphics.blitSprite(
                            RenderPipelines.GUI_TEXTURED,
                            Hud.getMobEffectSprite(effect),
                            iconX, iconY,
                            (int) ICON_SIZE, (int) ICON_SIZE);
                }

                // --- Progress bar with glow ---
                if (info.durationTimer.value > 0) {
                    int barWidth = (int) Math.min(info.durationTimer.value, w);
                    int barGlow = (accentColor & 0x00FFFFFF) | 0x25 << 24;
                    CustomRoundedRectRenderer.drawRoundedRect(graphics,
                            0, h - 3, w, 3, 1, barGlow);
                    CustomRoundedRectRenderer.drawRoundedRect(graphics,
                            0, h - 2, barWidth, 2, 1,
                            (accentColor & 0x00FFFFFF) | 0xBB000000);
                }

                // --- Text ---
                graphics.pose().pushMatrix();
                float textScale = 0.85F;
                graphics.pose().scale(textScale, textScale);

                String name = getDisplayName(effect.value(), info).getString();
                float textX = (6 + ICON_SIZE + 10) / textScale;
                int nameLen = name.length();

                int nameTopColor = lighten(accentColor, 0.35f);
                CustomFontRenderer.drawString(graphics, mc.font, name,
                        textX, 6.0F / textScale,
                        i -> lerpColor(nameTopColor, accentColor, (float) i / Math.max(1, nameLen - 1)));

                String duration = StringUtil.formatTickDuration(info.duration, 20);
                graphics.text(mc.font, duration,
                        (int) textX, (int) (18.0F / textScale), DURATION_TEXT_COLOR_RGB, true);

                graphics.pose().popMatrix();
                graphics.pose().popMatrix();

                currentY[0] += SPACING_Y;
            }

            if (!info.shouldDisappear) {
                maxWidth[0] = Math.max(maxWidth[0], info.width);
            }

            if (info.shouldDisappear) {
                return info.xTimer.value <= -info.width - 15.0F;
            }
            return false;
        });

        // Register drag region
        float totalH = currentY[0] - initialY;
        if (totalH > 0 && maxWidth[0] > 0) {
            float regionX = rightAligned ? originX - maxWidth[0] - PADDING_X : originX;
            float regionW = maxWidth[0] + PADDING_X * 2;
            Gemini.hudDragManager.registerDragRegion(module, (int) regionX, (int) initialY, (int) regionW, (int) totalH);
        }
    }

    /**
     * Draws just the editor outline border (no dummy content) for enabled modules.
     */
    public void renderOutline(GuiGraphicsExtractor graphics, Module module) {
        Minecraft mc = Minecraft.getInstance();
        boolean rightAligned = Gemini.hudDragManager.isOnRightSide(module);
        float originX = module.hudX;
        float originY = module.hudY;

        String dummyName = "Speed II";
        float dummyWidth = mc.font.width(dummyName) * 0.85F + ICON_SIZE + 40.0F;
        float elementW = Math.max(dummyWidth, 120f);
        float totalW = elementW + PADDING_X * 2;

        float x = rightAligned ? originX - totalW : originX;

        CustomRoundedRectRenderer.drawRoundedOutline(graphics,
                (int) x, (int) originY, (int) totalW, (int) ELEMENT_HEIGHT,
                CORNER_RADIUS, 0xAAFFD700, 2);

        Gemini.hudDragManager.registerDragRegion(module, (int) x, (int) originY, (int) totalW, (int) ELEMENT_HEIGHT);
    }

    private static int lighten(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = (int) Math.min(255, r + (255 - r) * amount);
        g = (int) Math.min(255, g + (255 - g) * amount);
        b = (int) Math.min(255, b + (255 - b) * amount);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int color, float amount) {
        int r = (int) ((color >> 16 & 0xFF) * (1.0f - amount));
        int g = (int) ((color >> 8 & 0xFF) * (1.0f - amount));
        int b = (int) ((color & 0xFF) * (1.0f - amount));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, aa = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (aa + (bb - aa) * t);
        return (a & 0xFF000000) | (rr << 16) | (rg << 8) | rb;
    }

    private Component getDisplayName(MobEffect effect, MobEffectInfo info) {
        MutableComponent name = effect.getDisplayName().copy();
        if (info.amplifier >= 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + (info.amplifier + 1)));
        }
        return name;
    }
}

class MobEffectInfo {
    public SmoothAnimationTimer xTimer = new SmoothAnimationTimer(0.0F, 0.15F);
    public SmoothAnimationTimer yTimer = new SmoothAnimationTimer(0.0F, 0.15F);
    public SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(0.0F, 0.15F);

    public int duration = 0;
    public int maxDuration = 1;
    public int amplifier = 0;
    public float width = 0.0F;
    public int color = 0xFFFF5555;
    public boolean shouldDisappear = false;
}
