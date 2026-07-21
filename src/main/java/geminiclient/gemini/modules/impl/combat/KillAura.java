package geminiclient.gemini.modules.impl.combat;

import geminiclient.gemini.customRenderer.glsl.modules.KillAuraIndicatorRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.utils.MathHelper;
import geminiclient.gemini.utils.ReachUtils;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class KillAura extends Module {
    private final BoolValue noCoolDown = new BoolValue("NoCoolDown", false);
    private final IntRangeValue cps = new IntRangeValue("CPS", 10, 18, 1, 20, () -> noCoolDown.enabled);
    private final IntValue hurtTime = new IntValue("HurtTime", 10, 1, 10);
    private final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    private final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    private final FloatValue rotationSpeed = new FloatValue("RotationSpeed", 180f, 30f, 360f);
    private final ListValue pro = new ListValue("Priority", "Distance", new String[]{
            "Distance", "Health", "hurtTime"
    });
    private final CheckboxValue targets = new CheckboxValue("Targets", new BoolValue[]{
            new BoolValue("AttackPlayers", true), new BoolValue("AttackMobs", true),
            new BoolValue("AttackAnimals", false), new BoolValue("AttackDead")
    });
    private final CheckboxValue stop = new CheckboxValue("StopWorking", new BoolValue[]{
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    private final ListValue rotationMode = new ListValue("RotationMode", "Linear", new String[]{
            "Linear", "Exponential", "Smooth", "Adaptive"
    });
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);
    private final BoolValue requireAim = new BoolValue("RequireAim", false);
    private final ListValue aimMode = new ListValue("AimMode", "Head", new String[]{
            "Head", "Chest", "Body", "Legs", "Random"
    });

    // Target visuals
    private final BoolValue targetIndicator = new BoolValue("TargetIndicator", true);
    private final ListValue indicatorStyle = new ListValue("IndicatorStyle", "Arcane Array",
            new String[]{"Arcane Array", "Energy Helix", "Health Ring"},
            () -> targetIndicator.enabled);
    private final BoolValue orbitingOrbsEffect = new BoolValue("Comet Vortex", true);
    private final BoolValue pulseSphereEffect = new BoolValue("Prismatic Cage");
    private final BoolValue runeCrownEffect = new BoolValue("Rune Crown", true);
    private final CheckboxValue targetEffects = new CheckboxValue("TargetEffects", new BoolValue[]{
            orbitingOrbsEffect, pulseSphereEffect, runeCrownEffect
    });
    private final ListValue indicatorTargets = new ListValue("IndicatorTargets", "Current",
            new String[]{"Current", "All"});
    private final ListValue indicatorColorMode = new ListValue("IndicatorColors", "Health",
            new String[]{"Health", "Custom", "Rainbow"});
    private final ColorValue indicatorPrimaryColor = new ColorValue("IndicatorPrimary", 0xFF64E8FF,
            () -> indicatorColorMode.is("Custom"));
    private final ColorValue indicatorSecondaryColor = new ColorValue("IndicatorSecondary", 0xFFC06CFF,
            () -> indicatorColorMode.is("Custom"));
    private final FloatValue indicatorRainbowSpeed = new FloatValue("IndicatorRainbowSpeed",
            0.35f, 0.0f, 2.0f, () -> indicatorColorMode.is("Rainbow"));
    private final IntValue indicatorParticleCount = new IntValue("IndicatorParticles", 36, 6, 64);
    private final FloatValue indicatorRadius = new FloatValue("IndicatorRadius", 1.25f, 0.5f, 2.5f);
    private final FloatValue indicatorParticleSize = new FloatValue("IndicatorParticleSize",
            0.065f, 0.015f, 0.20f);
    private final FloatValue indicatorOpacity = new FloatValue("IndicatorOpacity", 0.88f, 0.05f, 1.0f);
    private final FloatValue indicatorRotationSpeed = new FloatValue("IndicatorRotationSpeed",
            0.45f, -2.5f, 2.5f);
    private final FloatValue indicatorPulse = new FloatValue("IndicatorPulse", 0.22f, 0.0f, 1.0f);
    private final FloatValue indicatorPulseSpeed = new FloatValue("IndicatorPulseSpeed",
            1.4f, 0.0f, 5.0f);
    private final FloatValue indicatorYOffset = new FloatValue("IndicatorYOffset", 0.0f, -0.5f, 1.0f);
    private final FloatValue indicatorHelixHeight = new FloatValue("IndicatorHelixHeight",
            1.0f, 0.2f, 1.5f,
            () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));
    private final FloatValue indicatorHelixTurns = new FloatValue("IndicatorHelixTurns",
            1.8f, 0.5f, 4.0f,
            () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));
    private final BoolValue indicatorDoubleHelix = new BoolValue("IndicatorDoubleHelix",
            true, () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));

    private final List<Entity> entities = new CopyOnWriteArrayList<>();
    private Entity curr;
    private final TimerUtils attackTimer = new TimerUtils();
    private long indicatorAnimationStartNanos;

    // [修复] 缓存下一次攻击的延迟时间，防止每次 Update 时随机数跳动
    private long nextAttackDelay = 0;

    float serverYaw, serverPitch;

    // Smooth mode state
    private float smoothStartYaw, smoothStartPitch, smoothProgress;
    private Entity smoothTarget;

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop, rotationSpeed, rotationMode,
                silentRotate, requireAim, aimMode,
                targetIndicator, indicatorStyle, targetEffects,
                indicatorTargets, indicatorColorMode,
                indicatorPrimaryColor, indicatorSecondaryColor, indicatorRainbowSpeed,
                indicatorParticleCount, indicatorRadius, indicatorParticleSize,
                indicatorOpacity, indicatorRotationSpeed, indicatorPulse,
                indicatorPulseSpeed, indicatorYOffset,
                indicatorHelixHeight, indicatorHelixTurns, indicatorDoubleHelix);
    }

    @Override
    public void onEnabled() {
        smoothTarget = null;
        smoothProgress = 0f;
        indicatorAnimationStartNanos = System.nanoTime();
        nextAttackDelay = getRandomDelay(); // 初始化第一次延迟
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        }
    }

    @Override
    public void onDisabled() {
        Gemini.rotationManager.releaseRotation(this);
        smoothTarget = null;
        indicatorAnimationStartNanos = 0L;
    }

    @SuppressWarnings("unused")
    @EventTarget(5)
    public void onMotion(MotionEvent event) {
        if (curr == null || mc.player == null || !stopWorkingMethod())
            return;
        if (event.getTimeEnum() == TimeEnum.Pre) {
            if (!silentRotate.enabled) {
                mc.player.setYRot(serverYaw);
                mc.player.setXRot(serverPitch);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.gameMode == null)
            return;

        findTargets();

        if (curr != null && stopWorkingMethod()) {
            updateTargetAngles(curr);
            Gemini.rotationManager.requestRotation(this, serverYaw, serverPitch,
                    RotationManager.PRIORITY_KILLAURA, true);

            if (requireAim.enabled && !isLookingAtTarget(curr))
                return;

            if (noCoolDown.enabled) {
                // [修复] 使用固定的 nextAttackDelay 进行比较，而不是一直生成新随机数
                if (attackTimer.getTimeElapsed() >= nextAttackDelay) {
                    mc.gameMode.attack(mc.player, curr);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    attackTimer.reset();
                    nextAttackDelay = getRandomDelay(); // 攻击后重置下一次的随机延迟
                }
            } else {
                // 原版冷却模式
                if (mc.player.getAttackStrengthScale(0.5f) >= 1.0f) {
                    mc.gameMode.attack(mc.player, curr);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
        } else {
            Gemini.rotationManager.releaseRotation(this);
        }
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (curr == null || entities.isEmpty()) return;

        List<Entity> renderTargets = indicatorTargets.is("Current")
                ? List.of(curr)
                : entities;
        int particlesPerTarget = indicatorParticleCount.getValue();
        int effectsPerTarget = getEnabledTargetEffectCount();
        if (effectsPerTarget == 0) return;
        float[] particleData = new float[
                renderTargets.size() * particlesPerTarget * effectsPerTarget
                        * KillAuraIndicatorRenderer.PARTICLE_STRIDE];
        float time = getIndicatorAnimationTime();
        int dataOffset = 0;

        for (Entity entity : renderTargets) {
            if (!(entity instanceof LivingEntity living) || !entity.isAlive()) continue;

            Vec3 position = entity.getPosition(event.partialTick());
            float health = living.getMaxHealth() <= 0f
                    ? 0f
                    : clamp01(living.getHealth() / living.getMaxHealth());
            float radius = Math.max(0.05f, entity.getBbWidth() * indicatorRadius.getValue());
            float particleSize = Math.max(0.008f,
                    entity.getBbWidth() * indicatorParticleSize.getValue());
            double baseY = position.y + indicatorYOffset.getValue();

            if (targetIndicator.enabled) {
                if (indicatorStyle.is("Arcane Array")) {
                    dataOffset = appendArcaneArray(
                            particleData, dataOffset, particlesPerTarget,
                            position.x, baseY, position.z,
                            radius, particleSize, entity.getBbHeight(), health, time);
                } else if (indicatorStyle.is("Energy Helix")) {
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
            if (orbitingOrbsEffect.enabled) {
                dataOffset = appendOrbitingOrbs(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
            if (pulseSphereEffect.enabled) {
                dataOffset = appendPulseSphere(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
            if (runeCrownEffect.enabled) {
                dataOffset = appendRuneCrown(
                        particleData, dataOffset, particlesPerTarget,
                        position.x, baseY, position.z,
                        radius, particleSize, entity.getBbHeight(), health, time);
            }
        }

        int particleCount = dataOffset / KillAuraIndicatorRenderer.PARTICLE_STRIDE;
        if (particleCount > 0) {
            KillAuraIndicatorRenderer.drawIndicators(
                    event.poseStack(), particleData, particleCount);
        }
    }

    private int getEnabledTargetEffectCount() {
        int count = targetIndicator.enabled ? 1 : 0;
        if (orbitingOrbsEffect.enabled) count++;
        if (pulseSphereEffect.enabled) count++;
        if (runeCrownEffect.enabled) count++;
        return count;
    }

    private int appendHealthRing(float[] data, int offset, int count,
                                 double centerX, double centerY, double centerZ,
                                 float radius, float particleSize,
                                 float health, float time) {
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            float progress = i / (float) count;
            double wave = Math.sin(
                    time * indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 2.0);
            float pulse = 1f + indicatorPulse.getValue() * (float) wave;
            double angle = progress * Math.PI * 2.0 - Math.PI / 2.0 + rotation;
            float animatedRadius = radius
                    * (1f + indicatorPulse.getValue() * 0.10f * (float) wave);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * animatedRadius,
                    centerY,
                    centerZ + Math.sin(angle) * animatedRadius,
                    particleSize * Math.max(0.15f, pulse),
                    color,
                    i % 4 == 0
                            ? KillAuraIndicatorRenderer.MATERIAL_DIAMOND
                            : KillAuraIndicatorRenderer.MATERIAL_ORB,
                    (float) -angle);
        }
        return offset;
    }

    private int appendArcaneArray(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 1.2;
        double pulse = Math.sin(time * indicatorPulseSpeed.getValue() * Math.PI * 2.0);

        if (count > 0) {
            offset = appendParticle(
                    data, offset, centerX, centerY + 0.025, centerZ,
                    radius * (1.58f + indicatorPulse.getValue() * 0.04f * (float) pulse),
                    getIndicatorColor(0, count, 0.08f, health, time),
                    KillAuraIndicatorRenderer.MATERIAL_SIGIL,
                    (float) rotation);
        }
        if (count > 1) {
            offset = appendParticle(
                    data, offset, centerX, centerY + entityHeight * 0.53, centerZ,
                    radius * 0.70f,
                    getIndicatorColor(count / 2, count, 0.58f, health, time),
                    KillAuraIndicatorRenderer.MATERIAL_EYE,
                    (float) -rotation);
        }

        int glyphCount = Math.max(1, count - 2);
        for (int i = 2; i < count; i++) {
            int glyphIndex = i - 2;
            float progress = glyphIndex / (float) glyphCount;
            int layer = glyphIndex & 1;
            double angle = progress * Math.PI * 4.0
                    + rotation * (layer == 0 ? 1.0 : -0.72);
            double wave = Math.sin(progress * Math.PI * 6.0
                    + time * indicatorPulseSpeed.getValue() * Math.PI * 2.0);
            double glyphRadius = radius * (layer == 0 ? 1.16 : 0.88);
            double y = centerY + entityHeight * (0.18 + progress * 0.66)
                    + wave * radius * 0.09;
            float size = particleSize * (layer == 0 ? 1.45f : 1.05f)
                    * (1f + indicatorPulse.getValue() * 0.25f * (float) wave);
            int material = switch (glyphIndex % 5) {
                case 0 -> KillAuraIndicatorRenderer.MATERIAL_RUNE;
                case 1, 4 -> KillAuraIndicatorRenderer.MATERIAL_SPARK;
                case 2 -> KillAuraIndicatorRenderer.MATERIAL_CRESCENT;
                default -> KillAuraIndicatorRenderer.MATERIAL_DIAMOND;
            };

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * glyphRadius,
                    y,
                    centerZ + Math.sin(angle) * glyphRadius,
                    Math.max(particleSize * 0.35f, size),
                    getIndicatorColor(i, count, progress, health, time),
                    material,
                    (float) (-angle + wave * 0.35));
        }
        return offset;
    }

    private int appendEnergyHelix(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        int strands = indicatorDoubleHelix.enabled ? 2 : 1;
        int pointsPerStrand = (count + strands - 1) / strands;
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 2.0;
        float helixHeight = entityHeight * indicatorHelixHeight.getValue();

        for (int i = 0; i < count; i++) {
            int strand = i % strands;
            int step = i / strands;
            float progress = step / (float) Math.max(1, pointsPerStrand - 1);
            double pulseWave = Math.sin(
                    time * indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 4.0
                            + strand * Math.PI);
            double angle = progress * indicatorHelixTurns.getValue() * Math.PI * 2.0
                    + strand * Math.PI * 2.0 / strands
                    + rotation;
            float animatedRadius = radius
                    * (1f + indicatorPulse.getValue() * 0.16f * (float) pulseWave);
            float animatedSize = particleSize
                    * (1f + indicatorPulse.getValue() * 0.60f * (float) pulseWave);
            float colorProgress = (progress + strand / (float) strands) % 1f;
            int color = getIndicatorColor(i, count, colorProgress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * animatedRadius,
                    centerY + progress * helixHeight,
                    centerZ + Math.sin(angle) * animatedRadius,
                    Math.max(particleSize * 0.15f, animatedSize),
                    color,
                    strand == 0
                            ? KillAuraIndicatorRenderer.MATERIAL_SPARK
                            : KillAuraIndicatorRenderer.MATERIAL_CRESCENT,
                    (float) (-angle + strand * Math.PI * 0.5));
        }
        return offset;
    }

    private int appendOrbitingOrbs(float[] data, int offset, int count,
                                   double centerX, double centerY, double centerZ,
                                   float radius, float particleSize, float entityHeight,
                                   float health, float time) {
        int orbitCount = 3;
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 2.0;
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
                    time * indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 2.0 + orbit);
            float size = particleSize * (1.0f + indicatorPulse.getValue()
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
                    i % 5 == 0
                            ? KillAuraIndicatorRenderer.MATERIAL_CRESCENT
                            : KillAuraIndicatorRenderer.MATERIAL_SPARK,
                    (float) (-angle + orbit * Math.PI / 3.0));
        }
        return offset;
    }

    private int appendPulseSphere(float[] data, int offset, int count,
                                  double centerX, double centerY, double centerZ,
                                  float radius, float particleSize, float entityHeight,
                                  float health, float time) {
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 1.25;
        double pulseWave = Math.sin(
                time * indicatorPulseSpeed.getValue() * Math.PI * 2.0);
        double sphereRadius = radius
                * (1.02 + indicatorPulse.getValue() * 0.18 * pulseWave);
        double middleY = centerY + entityHeight * 0.5;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));

        for (int i = 0; i < count; i++) {
            float progress = (i + 0.5f) / count;
            double sphereY = 1.0 - progress * 2.0;
            double horizontalRadius = Math.sqrt(Math.max(0.0, 1.0 - sphereY * sphereY));
            double angle = i * goldenAngle + rotation;
            double x = Math.cos(angle) * horizontalRadius;
            double z = Math.sin(angle) * horizontalRadius;
            float sizePulse = 1.0f + indicatorPulse.getValue() * 0.55f
                    * (float) Math.sin(angle + time * Math.PI * 2.0);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + x * sphereRadius,
                    middleY + sphereY * entityHeight * 0.58,
                    centerZ + z * sphereRadius,
                    Math.max(particleSize * 0.20f, particleSize * sizePulse),
                    color,
                    i % 3 == 0
                            ? KillAuraIndicatorRenderer.MATERIAL_RUNE
                            : KillAuraIndicatorRenderer.MATERIAL_DIAMOND,
                    (float) (-angle + rotation * 0.35));
        }
        return offset;
    }

    private int appendRuneCrown(float[] data, int offset, int count,
                                double centerX, double centerY, double centerZ,
                                float radius, float particleSize, float entityHeight,
                                float health, float time) {
        double rotation = time * indicatorRotationSpeed.getValue() * Math.PI * 1.35;
        double crownY = centerY + entityHeight + radius * 0.28;
        int teeth = 6;

        for (int i = 0; i < count; i++) {
            float progress = i / (float) count;
            double angle = progress * Math.PI * 2.0 + rotation;
            double toothWave = Math.pow(Math.abs(Math.sin(angle * teeth * 0.5)), 3.0);
            double pulseWave = Math.sin(
                    time * indicatorPulseSpeed.getValue() * Math.PI * 2.0
                            + progress * Math.PI * 4.0);
            double crownRadius = radius * (0.66 + toothWave * 0.20);
            double y = crownY + radius * (0.05 + toothWave * 0.42)
                    + pulseWave * radius * indicatorPulse.getValue() * 0.05;
            float size = particleSize * (float) (0.78 + toothWave * 0.75);
            int color = getIndicatorColor(i, count, progress, health, time);

            offset = appendParticle(
                    data, offset,
                    centerX + Math.cos(angle) * crownRadius,
                    y,
                    centerZ + Math.sin(angle) * crownRadius,
                    Math.max(particleSize * 0.20f, size),
                    color,
                    toothWave > 0.58
                            ? KillAuraIndicatorRenderer.MATERIAL_CRESCENT
                            : KillAuraIndicatorRenderer.MATERIAL_RUNE,
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
                * indicatorOpacity.getValue();
        data[offset + 8] = material;
        data[offset + 9] = rotation;
        return offset + KillAuraIndicatorRenderer.PARTICLE_STRIDE;
    }

    private int getIndicatorColor(int index, int count, float progress,
                                  float health, float time) {
        if (indicatorColorMode.is("Custom")) {
            return lerpColor(
                    indicatorPrimaryColor.getColor(),
                    indicatorSecondaryColor.getColor(),
                    0.5f - 0.5f * (float) Math.cos(progress * Math.PI * 2.0));
        }
        if (indicatorColorMode.is("Rainbow")) {
            float hue = (progress + time * indicatorRainbowSpeed.getValue()) % 1f;
            if (hue < 0f) hue += 1f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.82f, 1.0f) & 0xFFFFFF);
        }

        float redThreshold = (1f - health) * count;
        float dotHealth = smoothstep(redThreshold - 0.7f, redThreshold + 0.7f, index);
        if (dotHealth > 0.5f) {
            return lerpColor(0xFFFFE014, 0xFF26F233, (dotHealth - 0.5f) * 2f);
        }
        return lerpColor(0xFFFF1F14, 0xFFFFE014, dotHealth * 2f);
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

    private long getRandomDelay() {
        if (!noCoolDown.enabled)
            return 0;

        int minCps = cps.getMinValue();
        int maxCps = cps.getMaxValue();
        int cpsVal = minCps + (int) (Math.random() * (maxCps - minCps + 1));

        return (long) (1000.0 / Math.max(1, cpsVal));
    }

    private void findTargets() {
        if (mc.level == null || mc.player == null)
            return;

        entities.clear();
        curr = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            // 注意：这里假设你的 ReachUtils 返回的是实际距离的平方，如果是返回单边距离，请将 range.getValue() * range.getValue() 改回 range.getValue()
            if (isValidTarget(entity) && isInFov(entity) &&
                    ReachUtils.getMinDistanceBetweenEntities(mc.player.getBoundingBox(), 0.1, mc.player.getX(), mc.player.getY(), mc.player.getZ(), entity.getBoundingBox(), 0.0, entity.getX(), entity.getY(), entity.getZ()) < range.getValue() * range.getValue()) {
                entities.add(entity);
            }
        }

        if (entities.isEmpty())
            return;

        switch (pro.get()) {
            case "Distance":
                entities.sort(Comparator.comparingDouble(mc.player::distanceTo));
                break;
            case "Health":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.getHealth() : 0.0));
                break;
            case "hurtTime":
                entities.sort(
                        Comparator.comparingDouble(e -> e instanceof LivingEntity living ? living.hurtTime : 0.0));
                break;
        }

        curr = entities.getFirst(); // 考虑到兼容性，通常使用 get(0) 替代 Java21 的 getFirst()
    }

    private boolean stopWorkingMethod() {
        if (mc.player == null)
            return false;
        if (mc.player.isUsingItem() && stop.boolValues[0].enabled)
            return false;
        return mc.screen == null || !stop.boolValues[1].enabled;
    }

    private boolean isValidTarget(Entity entity) {
        if (mc.player == null)
            return false;
        if (entity == null || entity == mc.player)
            return false;
        if (!(entity instanceof LivingEntity))
            return false;
        if (((LivingEntity) entity).hurtTime > hurtTime.getValue())
            return false;
        if (entity instanceof ArmorStand)
            return false;
        if (entity.isAlive() || targets.boolValues[3].enabled) {
            if (entity instanceof Player && targets.boolValues[0].enabled)
                return true;
            if ((entity instanceof Mob || entity instanceof Slime || entity instanceof Bat)
                    && targets.boolValues[1].enabled)
                return true;
            return entity instanceof Animal && targets.boolValues[2].enabled;
        }
        return false;
    }

    private boolean isInFov(Entity entity) {
        if (mc.player == null)
            return false;
        if (fov.getValue() >= 360f)
            return true;
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        double yawToEntity = Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        double yawDiff = Math.abs(((mc.player.getYRot() - yawToEntity) % 360 + 540) % 360 - 180);
        return yawDiff <= fov.getValue() / 2;
    }

    private void updateTargetAngles(Entity entity) {
        if (mc.player == null)
            return;

        double targetX = entity.getX();
        double targetZ = entity.getZ();
        double targetY;

        switch (aimMode.get()) {
            case "Chest":
                targetY = entity.getY() + entity.getBbHeight() * 0.65;
                break;
            case "Body":
                targetY = entity.getY() + entity.getBbHeight() * 0.5;
                break;
            case "Legs":
                targetY = entity.getY() + entity.getBbHeight() * 0.2;
                break;
            case "Random":
                targetY = entity.getY() + entity.getBbHeight() * (0.1 + Math.random() * 0.85);
                double halfWidth = entity.getBbWidth() / 2.0;
                targetX += (Math.random() - 0.5) * halfWidth;
                targetZ += (Math.random() - 0.5) * halfWidth;
                break;
            case "Head":
            default:
                targetY = entity.getY() + entity.getEyeHeight();
        }

        final double xSize = targetX - mc.player.getX();
        final double ySize = targetY - (mc.player.getY() + mc.player.getEyeHeight());
        final double zSize = targetZ - mc.player.getZ();
        final double theta = MathHelper.sqrt_double(xSize * xSize + zSize * zSize);

        final float targetYaw = (float) (Math.atan2(zSize, xSize) * 180 / Math.PI) - 90;
        final float targetPitch = (float) (-(Math.atan2(ySize, theta) * 180 / Math.PI));

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

        float finalYaw = currentYaw + yawDiff;
        float finalPitch = net.minecraft.util.Mth.clamp(currentPitch + pitchDiff, -90.0f, 90.0f);

        applyRotation(finalYaw, finalPitch, entity);
    }

    private void applyRotation(float targetYaw, float targetPitch, Entity target) {
        float rotYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float rotPitchDiff = targetPitch - serverPitch;
        float speed = rotationSpeed.getValue();

        switch (rotationMode.get()) {
            case "Linear": {
                serverYaw += Math.copySign(Math.min(Math.abs(rotYawDiff), speed), rotYawDiff);
                serverPitch += Math.copySign(Math.min(Math.abs(rotPitchDiff), speed), rotPitchDiff);
                break;
            }
            case "Exponential": {
                float factor = Math.min(speed / 180f, 0.99f);
                serverYaw += rotYawDiff * factor;
                serverPitch += rotPitchDiff * factor;
                break;
            }
            case "Smooth": {
                if (target != smoothTarget) {
                    smoothTarget = target;
                    smoothStartYaw = serverYaw;
                    smoothStartPitch = serverPitch;
                    smoothProgress = 0f;
                }
                float totalYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - smoothStartYaw);
                float totalPitchDiff = targetPitch - smoothStartPitch;
                float totalDist = Math.abs(totalYawDiff) + Math.abs(totalPitchDiff);
                if (totalDist > 0.5f) {
                    float duration = Math.max(totalDist / speed, 3f);
                    smoothProgress += 1f / duration;
                    if (smoothProgress >= 1f) smoothProgress = 1f;
                    float t = smoothProgress;
                    float eased = t * t * (3f - 2f * t);
                    serverYaw = smoothStartYaw + totalYawDiff * eased;
                    serverPitch = smoothStartPitch + totalPitchDiff * eased;
                }
                break;
            }
            case "Adaptive": {
                float yawDist = Math.abs(rotYawDiff);
                float pitchDist = Math.abs(rotPitchDiff);
                float yawStep = speed * (0.5f + yawDist / 90f);
                float pitchStep = speed * (0.5f + pitchDist / 90f);
                serverYaw += Math.copySign(Math.min(yawDist, yawStep), rotYawDiff);
                serverPitch += Math.copySign(Math.min(pitchDist, pitchStep), rotPitchDiff);
                break;
            }
        }

        // [修复] 强制限制最终的 serverPitch 避免平滑计算越界翻转引发的反作弊拦截
        serverPitch = net.minecraft.util.Mth.clamp(serverPitch, -90f, 90f);
    }

    private boolean isLookingAtTarget(Entity entity) {
        if (mc.player == null)
            return false;

        float yaw = serverYaw;
        float pitch = serverPitch;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);

        // [修复] 增加一个极小值避免除以0导致的 NaN 或不可预测的 Infinity
        if (dx == 0) dx = 0.0001;
        if (dy == 0) dy = 0.0001;
        if (dz == 0) dz = 0.0001;

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getY() + mc.player.getEyeHeight();
        double eyeZ = mc.player.getZ();

        AABB bb = entity.getBoundingBox().inflate(0.1);

        double tMin = 0.0;
        double tMax = range.getValue();

        // X slab
        double t1 = (bb.minX - eyeX) / dx;
        double t2 = (bb.maxX - eyeX) / dx;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);
        if (tMin > tMax) return false;

        // Y slab
        t1 = (bb.minY - eyeY) / dy;
        t2 = (bb.maxY - eyeY) / dy;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);
        if (tMin > tMax) return false;

        // Z slab
        t1 = (bb.minZ - eyeZ) / dz;
        t2 = (bb.maxZ - eyeZ) / dz;
        if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
        tMin = Math.max(tMin, t1);
        tMax = Math.min(tMax, t2);

        return tMin <= tMax;
    }
}
