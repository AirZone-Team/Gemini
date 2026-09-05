package geminiclient.gemini.modules.impl.combat.killaura;

import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraTargetRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「Sorcery Array / 魔导咒阵」目标指示器图层生成。
 *
 * <p>旧的三套指示器样式与三个附加特效已被整体替换为一个签名特效，由六个
 * 可开关图层组成：地面法阵与身后咒环（Slang 着色器绘制，经
 * {@link KillAuraTargetRenderer} 批量提交）、环绕彗星 / 符文王冠 / 星屑
 * （真 3D 粒子，走 {@link KillAuraIndicatorRenderer} 批量管线），以及攻击
 * 脉冲与锁定闪光（着色器内驱动）。</p>
 *
 * <p>主题色板与 MagicHalo 同源：七主题（Seraphic / Arcane / Cyber / Void /
 * Inferno / Frost / Prism）使用与 MagicHalo stylePalette 完全相同的色值，
 * 使目标指示器与头顶光环风格统一；另有 Custom / Rainbow / Health 模式。
 * 粒子层的 CPU 配色镜像自着色器内的 stylePalette。</p>
 */
public final class TargetIndicatorRenderer {
    private static final long ATTACK_PULSE_NANOS = 350_000_000L;
    private static final long ACQUIRE_FLASH_NANOS = 450_000_000L;

    /** 每主题 {primary, secondary, accent}，镜像 killaura_target.frag.slang 的 stylePalette。 */
    private static final int[][] STYLE_PALETTES = {
            {0xFFFFA82E, 0xFFFF47B3, 0xFFFFFAD1}, // 0 Seraphic
            {0xFF8F45FF, 0xFF1FE0FF, 0xFFFFCC57}, // 1 Arcane
            {0xFF14EBFF, 0xFFFF1FA3, 0xFFE0FFFF}, // 2 Cyber
            {0xFF4F14C2, 0xFFF00A4D, 0xFFD1ADFF}, // 3 Void
            {0xFFFF4704, 0xFFFFB80F, 0xFFFFF59E}, // 4 Inferno
            {0xFF38B8FF, 0xFF8FF5FF, 0xFFF5FFFF}, // 5 Frost
            {0xFFFF38AD, 0xFF2ED1FF, 0xFFFFE04D}, // 6 Prism
    };

    private final KillAuraSettings settings;

    private long indicatorAnimationStartNanos;
    private long lastAttackNanos;
    private final Map<Integer, Long> acquireNanos = new HashMap<>();

    public TargetIndicatorRenderer(KillAuraSettings settings) {
        this.settings = settings;
    }

    /** 归零动画与命中状态（禁用时调用）。 */
    public void reset() {
        indicatorAnimationStartNanos = 0L;
        lastAttackNanos = 0L;
        acquireNanos.clear();
    }

    /** KillAura 命中目标时调用，驱动地面法阵的攻击脉冲。 */
    public void onAttack() {
        lastAttackNanos = System.nanoTime();
    }

    /** 把 renderTargets 全部转成着色器 quad 与粒子并绘制。 */
    public void render(PoseStack poseStack, List<Entity> renderTargets,
                       Entity currentTarget, float partialTick) {
        float time = getAnimationTime();
        long now = System.nanoTime();
        ThemeSnapshot theme = resolveTheme();
        float attackPulse = settings.attackPulseLayer.enabled
                ? decay01(now - lastAttackNanos, ATTACK_PULSE_NANOS)
                : 0f;

        List<KillAuraTargetRenderer.TargetQuad> quads = new ArrayList<>();
        int particleLayers = (settings.orbitCometsLayer.enabled ? 1 : 0)
                + (settings.runeCrownLayer.enabled ? 1 : 0)
                + (settings.starMotesLayer.enabled ? 1 : 0);
        int perLayerBudget = particleLayers > 0
                ? Math.max(4, settings.indicatorParticleCount.getValue() / particleLayers)
                : 0;
        float[] particleData = new float[Math.max(1,
                renderTargets.size() * perLayerBudget * particleLayers
                        * KillAuraIndicatorRenderer.PARTICLE_STRIDE)];
        int dataOffset = 0;
        Set<Integer> trackedIds = new HashSet<>();

        for (Entity entity : renderTargets) {
            if (!(entity instanceof LivingEntity living) || !entity.isAlive()) continue;

            int entityId = entity.getId();
            trackedIds.add(entityId);
            long acquireStart = acquireNanos.computeIfAbsent(entityId, key -> now);
            float acquire = settings.acquireFlashLayer.enabled
                    ? decay01(now - acquireStart, ACQUIRE_FLASH_NANOS)
                    : 0f;
            float health = living.getMaxHealth() <= 0f
                    ? 0f
                    : clamp01(living.getHealth() / living.getMaxHealth());
            int seed = entityId * 37 & 0xFF;
            int tint = entity == currentTarget || currentTarget == null
                    ? 0xFFFFFFFF
                    : 0xE6FFFFFF;

            Vec3 position = entity.getPosition(partialTick);
            float bbWidth = Math.max(0.3f, entity.getBbWidth());
            float bbHeight = Math.max(0.5f, entity.getBbHeight());
            double baseY = position.y + settings.indicatorYOffset.getValue();

            if (settings.groundSigilLayer.enabled) {
                double groundY = JumpCircleRenderer.findGroundY(
                        position.x, position.y, position.z)
                        + settings.indicatorYOffset.getValue() + 0.02;
                float sigilHalf = Math.max(0.35f,
                        bbWidth * settings.indicatorRadius.getValue() * 1.55f);
                quads.add(new KillAuraTargetRenderer.TargetQuad(
                        position.x, groundY, position.z, sigilHalf,
                        KillAuraTargetRenderer.MATERIAL_SIGIL, tint,
                        health, acquire, seed));
            }
            if (settings.auraRingLayer.enabled) {
                float ringHalf = bbHeight * 0.85f;
                quads.add(new KillAuraTargetRenderer.TargetQuad(
                        position.x, baseY + bbHeight * 0.55, position.z, ringHalf,
                        KillAuraTargetRenderer.MATERIAL_RING, tint,
                        health, acquire, seed));
            }

            float radius = Math.max(0.05f, bbWidth * settings.indicatorRadius.getValue());
            float particleSize = Math.max(0.008f,
                    bbWidth * settings.indicatorParticleSize.getValue());

            if (settings.orbitCometsLayer.enabled) {
                dataOffset = appendOrbitComets(particleData, dataOffset, perLayerBudget,
                        position, baseY, radius, particleSize, bbHeight,
                        theme, health, time);
            }
            if (settings.runeCrownLayer.enabled) {
                dataOffset = appendRuneCrown(particleData, dataOffset, perLayerBudget,
                        position, baseY, radius, particleSize, bbHeight,
                        theme, health, time);
            }
            if (settings.starMotesLayer.enabled) {
                dataOffset = appendStarMotes(particleData, dataOffset, perLayerBudget,
                        position, baseY, radius, particleSize, bbHeight,
                        theme, health, time, entityId);
            }
        }

        acquireNanos.keySet().removeIf(id -> !trackedIds.contains(id));

        if (!quads.isEmpty()) {
            KillAuraTargetRenderer.draw(poseStack, quads,
                    new KillAuraTargetRenderer.Uniforms(
                            time,
                            theme.styleId(),
                            theme.colorMode(),
                            attackPulse,
                            theme.primary(),
                            theme.secondary(),
                            theme.accent(),
                            settings.indicatorOpacity.getValue(),
                            theme.rainbowSpeed(),
                            settings.indicatorGlow.getValue(),
                            settings.indicatorRotationSpeed.getValue(),
                            settings.indicatorPulse.getValue(),
                            0.18f,
                            0.78f));
        }
        int particleCount = dataOffset / KillAuraIndicatorRenderer.PARTICLE_STRIDE;
        if (particleCount > 0) {
            KillAuraIndicatorRenderer.drawIndicators(poseStack, particleData, particleCount);
        }
    }

    // ------------------------------------------------------------------
    // Particle layers
    // ------------------------------------------------------------------

    /**
     * 两条 ±26° 倾斜的椭圆轨道绕身体中段运行，反向旋转；每颗彗星带两节
     * 渐隐拖尾，走真 3D 位置以保留视差。
     */
    private int appendOrbitComets(float[] data, int offset, int count,
                                  Vec3 position, double baseY, float radius,
                                  float particleSize, float entityHeight,
                                  ThemeSnapshot theme, float health, float time) {
        int orbitCount = 2;
        // 每颗彗星占 头部+两节拖尾 三个槽位，严格不超出预算。
        int cometCount = Math.max(1, count / (orbitCount * 3));
        int emitted = 0;
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        double middleY = baseY + entityHeight * 0.52;

        for (int orbit = 0; orbit < orbitCount; orbit++) {
            double direction = orbit == 0 ? 1.0 : -0.85;
            double tilt = Math.toRadians(orbit == 0 ? 26.0 : -26.0);
            double orbitRadius = radius * (orbit == 0 ? 1.05 : 0.88);
            for (int comet = 0; comet < cometCount && emitted + 3 <= count; comet++) {
                float progress = (comet + orbit * 0.5f) / cometCount;
                double angle = progress * Math.PI * 2.0 + rotation * direction
                        + orbit * Math.PI;
                double pulseWave = Math.sin(
                        time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0
                                + progress * Math.PI * 2.0 + orbit);
                int color = particleColor(theme, progress, health, time);

                for (int trail = 0; trail < 3; trail++, emitted++) {
                    double trailAngle = angle - trail * 0.26 * direction;
                    double localX = Math.cos(trailAngle) * orbitRadius;
                    double localZ = Math.sin(trailAngle) * orbitRadius;
                    double trailAlpha = trail == 0 ? 1.0 : 1.0 - trail * 0.38;
                    float size = trail == 0
                            ? particleSize * (1.45f + settings.indicatorPulse.getValue()
                                    * 0.5f * (float) pulseWave)
                            : particleSize * (1.05f - trail * 0.25f);

                    offset = appendParticle(
                            data, offset,
                            position.x + localX,
                            middleY + localZ * Math.sin(tilt),
                            position.z + localZ * Math.cos(tilt),
                            Math.max(particleSize * 0.2f, size),
                            color,
                            trail == 0
                                    ? KillAuraIndicatorRenderer.MATERIAL_SPARK
                                    : KillAuraIndicatorRenderer.MATERIAL_ORB,
                            (float) (-trailAngle + Math.PI * 0.5),
                            (float) trailAlpha);
                }
            }
        }
        return offset;
    }

    /** 头顶悬浮的符文光环：缓慢公转 + 上下浮动 + 逐符文脉冲。 */
    private int appendRuneCrown(float[] data, int offset, int count,
                                Vec3 position, double baseY, float radius,
                                float particleSize, float entityHeight,
                                ThemeSnapshot theme, float health, float time) {
        int runeCount = Math.max(4, Math.min(10, count));
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 1.4;
        double crownY = baseY + entityHeight + radius * 0.24;

        for (int i = 0; i < runeCount; i++) {
            float progress = i / (float) runeCount;
            double angle = progress * Math.PI * 2.0 + rotation;
            double bob = Math.sin(progress * Math.PI * 4.0
                    + time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0)
                    * radius * 0.06;
            float pulse = 1f + settings.indicatorPulse.getValue() * 0.35f
                    * (float) Math.sin(time * Math.PI * 2.0 * 1.6 + progress * Math.PI * 2.0);

            offset = appendParticle(
                    data, offset,
                    position.x + Math.cos(angle) * radius * 0.82,
                    crownY + bob,
                    position.z + Math.sin(angle) * radius * 0.82,
                    Math.max(particleSize * 0.3f, particleSize * 1.15f * pulse),
                    particleColor(theme, progress, health, time),
                    KillAuraIndicatorRenderer.MATERIAL_RUNE,
                    (float) (-angle + Math.PI * 0.5),
                    1f);
        }
        return offset;
    }

    /** 星屑：贴着身体的圆柱壳内闪烁上飘的四芒星，点缀色染色。 */
    private int appendStarMotes(float[] data, int offset, int count,
                                Vec3 position, double baseY, float radius,
                                float particleSize, float entityHeight,
                                ThemeSnapshot theme, float health, float time,
                                int entityId) {
        for (int i = 0; i < count; i++) {
            float seed = frac((entityId * 31 + i * 137) * 0.6180339887f);
            double angle = seed * Math.PI * 2.0
                    + time * 0.18 * (seed > 0.5 ? 1.0 : -1.0);
            double moteRadius = radius * (0.55 + 0.45 * frac(seed * 7.31f));
            float cycle = frac(time * 0.14f + seed);
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f
                    * (float) Math.sin(time * (2.0 + seed * 3.0) + seed * 40.0));

            offset = appendParticle(
                    data, offset,
                    position.x + Math.cos(angle) * moteRadius,
                    baseY + entityHeight * 0.05 + cycle * entityHeight * 1.15,
                    position.z + Math.sin(angle) * moteRadius,
                    Math.max(particleSize * 0.25f, particleSize * (0.7f + seed * 0.6f)),
                    theme.accent(),
                    KillAuraIndicatorRenderer.MATERIAL_STAR,
                    seed * (float) Math.PI * 2.0f + time * 0.35f,
                    twinkle * (1.0f - cycle * 0.45f));
        }
        return offset;
    }

    private int appendParticle(float[] data, int offset,
                               double x, double y, double z,
                               float halfSize, int argb,
                               int material, float rotation, float layerAlpha) {
        data[offset] = (float) x;
        data[offset + 1] = (float) y;
        data[offset + 2] = (float) z;
        data[offset + 3] = halfSize;
        data[offset + 4] = ((argb >> 16) & 0xFF) / 255f;
        data[offset + 5] = ((argb >> 8) & 0xFF) / 255f;
        data[offset + 6] = (argb & 0xFF) / 255f;
        data[offset + 7] = clamp01(((argb >>> 24) & 0xFF) / 255f * layerAlpha)
                * settings.indicatorOpacity.getValue();
        data[offset + 8] = material;
        data[offset + 9] = rotation;
        return offset + KillAuraIndicatorRenderer.PARTICLE_STRIDE;
    }

    // ------------------------------------------------------------------
    // Theming
    // ------------------------------------------------------------------

    private record ThemeSnapshot(int styleId, int colorMode, int primary,
                                 int secondary, int accent, float rainbowSpeed) {}

    /**
     * 解析主题：七主题取内置色板（与 MagicHalo 的 stylePalette 同源同值）；
     * Custom / Rainbow / Health 就地解析。
     */
    private ThemeSnapshot resolveTheme() {
        // 与 MagicHalo 模块的 Style 列表同序：Seraphic=0 … Prism=6。
        String[] names = {"Seraphic", "Arcane", "Cyber", "Void", "Inferno", "Frost", "Prism"};
        for (int i = 0; i < names.length; i++) {
            if (settings.theme.is(names[i])) {
                int[] palette = STYLE_PALETTES[i];
                return new ThemeSnapshot(i, 0, palette[0], palette[1], palette[2], 0.7f);
            }
        }
        if (settings.theme.is("Custom")) {
            return new ThemeSnapshot(0, 1,
                    settings.themePrimaryColor.getColor(),
                    settings.themeSecondaryColor.getColor(),
                    settings.themeAccentColor.getColor(), 0.7f);
        }
        if (settings.theme.is("Rainbow")) {
            return new ThemeSnapshot(0, 2,
                    0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                    settings.themeRainbowSpeed.getValue());
        }
        // Health
        return new ThemeSnapshot(0, 3, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0.7f);
    }

    /** 粒子 CPU 配色：Style/Custom 主次色 shimmer，Rainbow 光谱，Health 血量色。 */
    private int particleColor(ThemeSnapshot theme, float progress,
                              float health, float time) {
        float wave = 0.5f + 0.5f * (float) Math.cos(
                progress * Math.PI * 2.0
                        - time * settings.indicatorPulseSpeed.getValue() * Math.PI);
        float brightness = 0.78f + 0.28f * wave * wave;

        int base;
        if (theme.colorMode() == 2) {
            float phase = progress + time * theme.rainbowSpeed() * 0.16f;
            base = spectralColor(phase);
        } else if (theme.colorMode() == 3) {
            base = health >= 0.5f
                    ? lerpColor(0xFFFFD60A, 0xFF34E34F, (health - 0.5f) * 2f)
                    : lerpColor(0xFFFF453A, 0xFFFFD60A, health * 2f);
        } else {
            // Style 与 Custom 共用主→次 shimmer。
            float shimmer = smoothstep(0.80f, 1.0f, wave) * 0.7f;
            base = lerpColor(theme.primary(), theme.secondary(), shimmer);
        }
        return scaleBrightness(base, brightness);
    }

    private static int spectralColor(float phase) {
        int r = Math.round((0.58f + 0.42f * (float) Math.cos(Math.PI * 2.0 * phase)) * 255f);
        int g = Math.round((0.58f + 0.42f * (float) Math.cos(Math.PI * 2.0 * (phase + 0.33f))) * 255f);
        int b = Math.round((0.58f + 0.42f * (float) Math.cos(Math.PI * 2.0 * (phase + 0.67f))) * 255f);
        return 0xFF000000
                | (Math.max(0, Math.min(255, r)) << 16)
                | (Math.max(0, Math.min(255, g)) << 8)
                | Math.max(0, Math.min(255, b));
    }

    // ------------------------------------------------------------------
    // Misc helpers
    // ------------------------------------------------------------------

    private float getAnimationTime() {
        if (indicatorAnimationStartNanos == 0L) {
            indicatorAnimationStartNanos = System.nanoTime();
        }
        return (System.nanoTime() - indicatorAnimationStartNanos) / 1_000_000_000f;
    }

    private static float decay01(long elapsedNanos, long durationNanos) {
        if (elapsedNanos < 0L || elapsedNanos >= durationNanos) return 0f;
        return 1f - elapsedNanos / (float) durationNanos;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float frac(float value) {
        return value - (float) Math.floor(value);
    }

    private static int scaleBrightness(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int lerpColor(int from, int to, float amount) {
        float t = clamp01(amount);
        int r = Math.round(((from >> 16) & 0xFF)
                + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF)
                + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF)
                + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
