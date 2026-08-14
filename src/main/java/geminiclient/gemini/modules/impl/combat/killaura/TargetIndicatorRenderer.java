package geminiclient.gemini.modules.impl.combat.killaura;

import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;

/**
 * 目标指示粒子生成：把当前/全部目标渲染成 KillAuraIndicatorRenderer 的
 * 紧凑粒子数组（Arcane Array / Energy Helix / Health Ring + 三个环绕效果），
 * 并负责颜色模式（Health / Custom / Rainbow）与亮度波动的数学。
 */
public final class TargetIndicatorRenderer {
    private final KillAuraSettings settings;

    private long indicatorAnimationStartNanos;

    public TargetIndicatorRenderer(KillAuraSettings settings) {
        this.settings = settings;
    }

    /** 归零动画时间（禁用时调用；首次渲染会自动初始化）。 */
    public void reset() {
        indicatorAnimationStartNanos = 0L;
    }

    /** 把 renderTargets 全部打包成粒子数组并交给渲染器绘制。 */
    public void render(PoseStack poseStack, List<Entity> renderTargets, float partialTick) {
        int particlesPerTarget = settings.indicatorParticleCount.getValue();
        int effectsPerTarget = getEnabledTargetEffectCount();
        if (effectsPerTarget == 0) return;
        float[] particleData = new float[
                renderTargets.size() * particlesPerTarget * effectsPerTarget
                        * KillAuraIndicatorRenderer.PARTICLE_STRIDE];
        float time = getIndicatorAnimationTime();
        int dataOffset = 0;

        for (Entity entity : renderTargets) {
            if (!(entity instanceof LivingEntity living) || !entity.isAlive()) continue;

            Vec3 position = entity.getPosition(partialTick);
            float health = living.getMaxHealth() <= 0f
                    ? 0f
                    : clamp01(living.getHealth() / living.getMaxHealth());
            float radius = Math.max(0.05f, entity.getBbWidth() * settings.indicatorRadius.getValue());
            float particleSize = Math.max(0.008f,
                    entity.getBbWidth() * settings.indicatorParticleSize.getValue());
            double baseY = position.y + settings.indicatorYOffset.getValue();

            if (settings.targetIndicator.enabled) {
                if (settings.indicatorStyle.is("Arcane Array")) {
                    dataOffset = appendArcaneArray(
                            particleData, dataOffset, particlesPerTarget,
                            position.x, baseY, position.z,
                            radius, particleSize, entity.getBbHeight(), health, time);
                } else if (settings.indicatorStyle.is("Energy Helix")) {
                    dataOffset = appendEnergyHelix(
                            particleData, dataOffset, particlesPerTarget,
                            position.x, baseY, position.z,
                            radius, particleSize, entity.getBbHeight(), health, time);
                } else {
                    dataOffset = appendHealthRing(
                            particleData, dataOffset, particlesPerTarget,
                            position.x, baseY, position.z,
                            radius, particleSize, health, time);
                }
            }
            if (settings.orbitingOrbsEffect.enabled) {
                dataOffset = appendOrbitingOrbs(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
            if (settings.pulseSphereEffect.enabled) {
                dataOffset = appendPulseSphere(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
            if (settings.runeCrownEffect.enabled) {
                dataOffset = appendRuneCrown(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
        }

        int particleCount = dataOffset / KillAuraIndicatorRenderer.PARTICLE_STRIDE;
        if (particleCount > 0) {
            KillAuraIndicatorRenderer.drawIndicators(
                    poseStack, particleData, particleCount);
        }
    }

    private int getEnabledTargetEffectCount() {
        int count = settings.targetIndicator.enabled ? 1 : 0;
        if (settings.orbitingOrbsEffect.enabled) count++;
        if (settings.pulseSphereEffect.enabled) count++;
        if (settings.runeCrownEffect.enabled) count++;
        return count;
    }

    private int appendHealthRing(float[] data, int offset, int count,
                                 double centerX, double centerY, double centerZ,
                                 float radius, float particleSize,
                                 float health, float time) {
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            float progress = i / (float) count;
            double wave = Math.sin(
                    time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 2.0);
            float pulse = 1f + settings.indicatorPulse.getValue() * (float) wave;
            double angle = progress * Math.PI * 2.0 - Math.PI / 2.0 + rotation;
            float animatedRadius = radius
                    * (1f + settings.indicatorPulse.getValue() * 0.10f * (float) wave);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * animatedRadius,
                    centerY,
                    centerZ + Math.sin(angle) * animatedRadius,
                    particleSize * Math.max(0.15f, pulse),
                    color,
                    KillAuraIndicatorRenderer.MATERIAL_ORB,
                    (float) -angle);
        }
        return offset;
    }

    private int appendArcaneArray(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 1.2;
        double pulse = Math.sin(time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0);

        // The ground sigil is the centerpiece of this style.
        if (count > 0) {
            offset = appendParticle(
                    data, offset, centerX, centerY + 0.025, centerZ,
                    radius * (1.58f + settings.indicatorPulse.getValue() * 0.04f * (float) pulse),
                    getIndicatorColor(0, count, 0f, health, time),
                    KillAuraIndicatorRenderer.MATERIAL_SIGIL,
                    (float) rotation);
        }

        // A single slow ring of crystals hovering at ankle height. One glyph
        // type and one hue keep the array composed instead of noisy.
        int glyphCount = Math.max(1, count - 1);
        for (int i = 1; i < count; i++) {
            float progress = (i - 1) / (float) glyphCount;
            double angle = progress * Math.PI * 2.0 - rotation * 0.6;
            double bob = Math.sin(progress * Math.PI * 4.0
                    + time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0)
                    * radius * 0.05;
            double glyphRadius = radius * 1.02f;
            float size = particleSize * 1.15f
                    * (1f + settings.indicatorPulse.getValue() * 0.20f * (float) pulse);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * glyphRadius,
                    centerY + entityHeight * 0.10 + bob,
                    centerZ + Math.sin(angle) * glyphRadius,
                    Math.max(particleSize * 0.35f, size),
                    getIndicatorColor(i, count, progress, health, time),
                    KillAuraIndicatorRenderer.MATERIAL_DIAMOND,
                    (float) -angle);
        }
        return offset;
    }

    private int appendEnergyHelix(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        int strands = settings.indicatorDoubleHelix.enabled ? 2 : 1;
        int pointsPerStrand = (count + strands - 1) / strands;
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        float helixHeight = entityHeight * settings.indicatorHelixHeight.getValue();

        for (int i = 0; i < count; i++) {
            int strand = i % strands;
            int step = i / strands;
            float progress = step / (float) Math.max(1, pointsPerStrand - 1);
            double pulseWave = Math.sin(
                    time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 4.0
                            + strand * Math.PI);
            double angle = progress * settings.indicatorHelixTurns.getValue() * Math.PI * 2.0
                    + strand * Math.PI * 2.0 / strands
                    + rotation;
            float animatedRadius = radius
                    * (1f + settings.indicatorPulse.getValue() * 0.16f * (float) pulseWave);
            float animatedSize = particleSize
                    * (1f + settings.indicatorPulse.getValue() * 0.60f * (float) pulseWave);
            float colorProgress = (progress + strand / (float) strands) % 1f;
            int color = getIndicatorColor(i, count, colorProgress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * animatedRadius,
                    centerY + progress * helixHeight,
                    centerZ + Math.sin(angle) * animatedRadius,
                    Math.max(particleSize * 0.15f, animatedSize),
                    color,
                    KillAuraIndicatorRenderer.MATERIAL_ORB,
                    (float) (-angle + strand * Math.PI * 0.5));
        }
        return offset;
    }

    private int appendOrbitingOrbs(float[] data, int offset, int count,
                                   double centerX, double centerY, double centerZ,
                                   float radius, float particleSize, float entityHeight,
                                   float health, float time) {
        int orbitCount = 3;
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        double middleY = centerY + entityHeight * 0.52;

        for (int i = 0; i < count; i++) {
            int orbit = i % orbitCount;
            int step = i / orbitCount;
            int pointsInOrbit = (count + orbitCount - 1 - orbit) / orbitCount;
            float progress = step / (float) Math.max(1, pointsInOrbit);
            double angle = progress * Math.PI * 2.0
                    + rotation * (orbit == 1 ? -1.15 : 1.0)
                    + orbit * Math.PI * 2.0 / orbitCount;
            double tilt = Math.toRadians(28.0 + orbit * 26.0);
            double orbitRadius = radius * (0.82 + orbit * 0.13);
            double localX = Math.cos(angle) * orbitRadius;
            double localZ = Math.sin(angle) * orbitRadius;
            double rotatedY = localZ * Math.sin(tilt);
            double rotatedZ = localZ * Math.cos(tilt);
            double pulseWave = Math.sin(
                    time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 2.0 + orbit);
            float size = particleSize * (1.0f + settings.indicatorPulse.getValue()
                    * 0.75f * (float) pulseWave);
            int color = getIndicatorColor(i, count,
                    (progress + orbit / (float) orbitCount) % 1f, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + localX,
                    middleY + rotatedY,
                    centerZ + rotatedZ,
                    Math.max(particleSize * 0.22f, size),
                    color,
                    KillAuraIndicatorRenderer.MATERIAL_ORB,
                    (float) (-angle + orbit * Math.PI / 3.0));
        }
        return offset;
    }

    private int appendPulseSphere(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 1.25;
        double pulseWave = Math.sin(
                time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0);
        double sphereRadius = radius
                * (1.02 + settings.indicatorPulse.getValue() * 0.18 * pulseWave);
        double middleY = centerY + entityHeight * 0.5;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            float progress = (i + 0.5f) / count;
            double sphereY = 1.0 - progress * 2.0;
            double horizontalRadius = Math.sqrt(Math.max(0.0, 1.0 - sphereY * sphereY));
            double angle = i * goldenAngle + rotation;
            double x = Math.cos(angle) * horizontalRadius;
            double z = Math.sin(angle) * horizontalRadius;
            float sizePulse = 1.0f + settings.indicatorPulse.getValue() * 0.55f
                    * (float) Math.sin(angle + time * Math.PI * 2.0);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + x * sphereRadius,
                    middleY + sphereY * entityHeight * 0.58,
                    centerZ + z * sphereRadius,
                    Math.max(particleSize * 0.20f, particleSize * sizePulse),
                    color,
                    KillAuraIndicatorRenderer.MATERIAL_DIAMOND,
                    (float) (-angle + rotation * 0.35));
        }
        return offset;
    }

    private int appendRuneCrown(float[] data, int offset, int count,
                                double centerX, double centerY, double centerZ,
                                float radius, float particleSize, float entityHeight,
                                float health, float time) {
        double rotation = time * settings.indicatorRotationSpeed.getValue() * Math.PI * 1.35;
        double crownY = centerY + entityHeight + radius * 0.28;
        int teeth = 6;

        for (int i = 0; i < count; i++) {
            float progress = i / (float) count;
            double angle = progress * Math.PI * 2.0 + rotation;
            double toothWave = Math.pow(Math.abs(Math.sin(angle * teeth * 0.5)), 3.0);
            double pulseWave = Math.sin(
                    time * settings.indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 4.0);
            double crownRadius = radius * (0.66 + toothWave * 0.20);
            double y = crownY + radius * (0.05 + toothWave * 0.42)
                    + pulseWave * radius * settings.indicatorPulse.getValue() * 0.05;
            float size = particleSize * (float) (0.78 + toothWave * 0.75);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * crownRadius,
                    y,
                    centerZ + Math.sin(angle) * crownRadius,
                    Math.max(particleSize * 0.20f, size),
                    color,
                    KillAuraIndicatorRenderer.MATERIAL_DIAMOND,
                    (float) (-angle + Math.PI * 0.5));
        }
        return offset;
    }

    private int appendParticle(float[] data, int offset,
                               double x, double y, double z,
                               float halfSize, int argb,
                               int material, float rotation) {
        data[offset] = (float) x;
        data[offset + 1] = (float) y;
        data[offset + 2] = (float) z;
        data[offset + 3] = halfSize;
        data[offset + 4] = ((argb >> 16) & 0xFF) / 255f;
        data[offset + 5] = ((argb >> 8) & 0xFF) / 255f;
        data[offset + 6] = (argb & 0xFF) / 255f;
        data[offset + 7] = ((argb >>> 24) & 0xFF) / 255f
                * settings.indicatorOpacity.getValue();
        data[offset + 8] = material;
        data[offset + 9] = rotation;
        return offset + KillAuraIndicatorRenderer.PARTICLE_STRIDE;
    }

    private int getIndicatorColor(int index, int count, float progress,
                                  float health, float time) {
        // One coherent hue per target; only brightness travels across the
        // particles, so the effect reads as a single flowing ribbon of light
        // instead of scattered confetti.
        float wave = 0.5f + 0.5f * (float) Math.cos(
                progress * Math.PI * 2.0
                        - time * settings.indicatorPulseSpeed.getValue() * Math.PI);
        float brightness = 0.74f + 0.30f * wave * wave;

        int base;
        if (settings.indicatorColorMode.is("Custom")) {
            // Mostly the primary tone; the secondary only shimmers through at
            // the crest of the brightness wave.
            float shimmer = smoothstep(0.80f, 1.0f, wave) * 0.7f;
            base = lerpColor(
                    settings.indicatorPrimaryColor.getColor(),
                    settings.indicatorSecondaryColor.getColor(),
                    shimmer);
        } else if (settings.indicatorColorMode.is("Rainbow")) {
            // The whole indicator drifts slowly through soft hues as one.
            float hue = (time * settings.indicatorRainbowSpeed.getValue()) % 1f;
            if (hue < 0f) hue += 1f;
            base = 0xFF000000 | (Color.HSBtoRGB(hue, 0.55f, 1.0f) & 0xFFFFFF);
        } else {
            base = health >= 0.5f
                    ? lerpColor(0xFFFFD60A, 0xFF34E34F, (health - 0.5f) * 2f)
                    : lerpColor(0xFFFF453A, 0xFFFFD60A, health * 2f);
        }
        return scaleBrightness(base, brightness);
    }

    private static int scaleBrightness(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private float getIndicatorAnimationTime() {
        if (indicatorAnimationStartNanos == 0L) {
            indicatorAnimationStartNanos = System.nanoTime();
        }
        return (System.nanoTime() - indicatorAnimationStartNanos) / 1_000_000_000f;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int lerpColor(int from, int to, float amount) {
        float t = clamp01(amount);
        int a = Math.round(((from >>> 24) & 0xFF)
                + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >> 16) & 0xFF)
                + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF)
                + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF)
                + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
