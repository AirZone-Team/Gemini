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
import geminiclient.gemini.utils.animation.SpringAnimation;
import geminiclient.gemini.values.impl.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import org.jetbrains.annotations.UnknownNullability;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

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
            new BoolValue("AttackAnimals", false), new BoolValue("AttackDead"),
            new BoolValue("AttackTeammates")
    });
    private final CheckboxValue stop = new CheckboxValue("StopWorking", new BoolValue[]{
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    private final ListValue rotationMode = new ListValue("RotationMode", "Linear", new String[]{
            "Linear", "Exponential", "Smooth", "Bezier", "Adaptive"
    });
    private final ListValue easingCurve = new ListValue("EasingCurve", "Smoothstep", new String[]{
            "Smoothstep", "EaseInOutCubic", "EaseOutCubic", "EaseOutExpo"
    }, () -> rotationMode.is("Smooth") || rotationMode.is("Bezier"));
    private final FloatValue overshoot = new FloatValue("Overshoot", 0f, 0f, 1f,
            () -> rotationMode.is("Smooth"));
    private final FloatValue bezierRandomness = new FloatValue("BezierRandomness", 0.12f, 0f, 0.35f,
            () -> rotationMode.is("Bezier"));
    private final FloatValue rotationJitter = new FloatValue("RotationJitter", 0.15f, 0f, 3f);
    private final FloatValue distanceSpread = new FloatValue("DistanceSpread", 0.75f, 0f, 2f,
            () -> rotationJitter.getValue() > 0f);
    private final FloatValue prediction = new FloatValue("Prediction", 1f, 0f, 5f);
    private final BoolValue silentRotate = new BoolValue("SilentRotate", true);
    private final BoolValue rayTrace = new BoolValue("RayTrace", false);
    private final BoolValue blockRayTrace = new BoolValue("BlockRayTrace", true,
            () -> rayTrace.enabled);
    private final ListValue aimMode = new ListValue("AimMode", "Head", new String[]{
            "Head", "Chest", "Body", "Legs", "Random"
    });
    private final FloatValue aimPointSmooth = new FloatValue("AimPointSmooth", 0.30f, 0.05f, 1f);
    private final IntValue randomAimInterval = new IntValue("RandomAimInterval", 10, 3, 40,
            () -> aimMode.is("Random"));
    private final BoolValue movementReaction = new BoolValue("MovementReaction", true);
    private final IntRangeValue reactionDelay = new IntRangeValue("ReactionDelay", 60, 110, 0, 300,
            () -> movementReaction.enabled);
    private final FloatValue repositionThreshold = new FloatValue("RepositionThreshold", 0.20f, 0.05f, 1.5f,
            () -> movementReaction.enabled);

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
    private float smoothEndYaw, smoothEndPitch;
    private Entity smoothTarget;

    // Bezier mode state
    private float bezierStartYaw, bezierStartPitch, bezierProgress;
    private float bezierControl1Yaw, bezierControl1Pitch;
    private float bezierControl2Yaw, bezierControl2Pitch;
    private float bezierEndYaw, bezierEndPitch;
    private Entity bezierTarget;

    private float jitterYaw, jitterPitch, jitterTargetYaw, jitterTargetPitch;
    private int jitterRefreshTicks;
    private Entity jitterTarget;

    private Entity aimPointTarget;
    private String aimPointMode;
    private Vec3 currentLocalAim;
    private Vec3 goalLocalAim;
    private int randomAimTicksRemaining;

    private Entity reactionTarget;
    private Vec3 lastObservedTargetPosition;
    private Vec3 lastObservedTargetVelocity;
    private Vec3 acceptedWorldAim;
    private Vec3 heldWorldAim;
    private Vec3 catchupWorldAim;
    private float heldYaw, heldPitch;
    private long reactionUntilNanos;
    private int reactionCooldownTicks;
    private int catchupTicksRemaining;
    private boolean reactionHolding;
    private boolean reactionCatchingUp;

    private static final int JITTER_MIN_REFRESH_INTERVAL = 3;
    private static final int JITTER_MAX_REFRESH_INTERVAL = 7;
    private static final float JITTER_LERP_FACTOR = 0.25f;
    private static final float SMOOTH_REPLAN_THRESHOLD = 3f;
    private static final float BEZIER_MIN_CONTROL_POSITION = 0.20f;
    private static final float BEZIER_MAX_CONTROL_POSITION = 0.40f;
    private static final float BEZIER_CONTROL_DISTANCE_CAP = 90f;
    private static final float BEZIER_SHORT_SEGMENT_SCALE = 12f;
    private static final int REACTION_COOLDOWN_TICKS = 4;
    private static final int MAX_CATCHUP_TICKS = 12;
    private static final double MAX_PREDICTION_VELOCITY = 1.5;

    public KillAura() {
        super("KillAura", ModuleEnum.Combat);
        addValue(noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop, rotationSpeed, rotationMode,
                easingCurve, overshoot, bezierRandomness, rotationJitter, distanceSpread, prediction,
                silentRotate, rayTrace, blockRayTrace, aimMode, aimPointSmooth, randomAimInterval,
                movementReaction, reactionDelay, repositionThreshold,
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
        resetRotationEffects();
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
        resetRotationEffects();
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

            if (rayTrace.enabled && !isLookingAtTarget(curr))
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
            resetRotationEffects();
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
            if (isValidTarget(entity) && isInFov(entity) &&
                    ReachUtils.distanceToSqr(entity) < range.getValue() * range.getValue()) {
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
        return mc.gui.screen() == null || !stop.boolValues[1].enabled;
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
            if (entity instanceof Player && targets.boolValues[4].enabled) {
                if (isTeammate(entity)) {
                    return false;
                }
            }
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

        boolean targetChanged = entity != aimPointTarget;
        if (targetChanged) {
            initializeAimPoint(entity);
            resetJitter();
        } else {
            updateAimPointGoal(entity);
        }
        if (!currentLocalAim.equals(goalLocalAim)) {
            currentLocalAim = currentLocalAim.lerp(goalLocalAim, aimPointSmooth.getValue());
        }

        Vec3 eyePosition = new Vec3(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(),
                mc.player.getZ());
        Vec3 liveWorldAim = localAimToWorld(entity, currentLocalAim);
        Vec3 predictedWorldAim = applyForwardPrediction(liveWorldAim);
        Vec3 reactedWorldAim = applyReactionDelay(
                entity, eyePosition, predictedWorldAim, targetChanged);

        Vec3 aimDelta = reactedWorldAim.subtract(eyePosition);
        double horizontalDistance = MathHelper.sqrt_double(
                aimDelta.x * aimDelta.x + aimDelta.z * aimDelta.z);
        float targetYaw = (float) (Math.atan2(aimDelta.z, aimDelta.x) * 180 / Math.PI) - 90;
        float targetPitch = (float) (-(Math.atan2(aimDelta.y, horizontalDistance) * 180 / Math.PI));

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
        float pitchDiff = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);
        updateRotationJitter(entity, eyePosition.distanceTo(reactedWorldAim));

        float finalYaw = currentYaw + yawDiff + jitterYaw;
        float finalPitch = net.minecraft.util.Mth.clamp(
                currentPitch + pitchDiff + jitterPitch, -90.0f, 90.0f);
        applyRotation(finalYaw, finalPitch, entity);
    }

    private void initializeAimPoint(Entity entity) {
        resetAimTracking();
        aimPointTarget = entity;
        aimPointMode = aimMode.get();
        goalLocalAim = selectLocalAimPoint(entity);
        currentLocalAim = goalLocalAim;
        randomAimTicksRemaining = aimMode.is("Random") ? randomAimInterval.getValue() : 0;
    }

    private void updateAimPointGoal(Entity entity) {
        String selectedMode = aimMode.get();
        if (!selectedMode.equals(aimPointMode)) {
            aimPointMode = selectedMode;
            goalLocalAim = selectLocalAimPoint(entity);
            randomAimTicksRemaining = aimMode.is("Random") ? randomAimInterval.getValue() : 0;
            return;
        }
        if (aimMode.is("Random") && --randomAimTicksRemaining <= 0) {
            goalLocalAim = selectLocalAimPoint(entity);
            randomAimTicksRemaining = randomAimInterval.getValue();
        }
    }

    private Vec3 selectLocalAimPoint(Entity entity) {
        return switch (aimMode.get()) {
            case "Chest" -> new Vec3(0.0, 0.65, 0.0);
            case "Body" -> new Vec3(0.0, 0.50, 0.0);
            case "Legs" -> new Vec3(0.0, 0.20, 0.0);
            case "Random" -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                yield new Vec3(
                        random.nextDouble(-0.25, 0.25),
                        random.nextDouble(0.15, 0.90),
                        random.nextDouble(-0.25, 0.25));
            }
            case "Head" -> new Vec3(0.0,
                    net.minecraft.util.Mth.clamp(
                            entity.getEyeHeight() / Math.max(entity.getBbHeight(), 0.001f),
                            0.0f, 1.0f),
                    0.0);
            default -> new Vec3(0.0, 0.50, 0.0);
        };
    }

    private Vec3 localAimToWorld(Entity entity, Vec3 localAim) {
        return new Vec3(
                entity.getX() + localAim.x * entity.getBbWidth(),
                entity.getY() + localAim.y * entity.getBbHeight(),
                entity.getZ() + localAim.z * entity.getBbWidth());
    }

    private Vec3 applyForwardPrediction(Vec3 worldAim) {
        if (mc.player == null || prediction.getValue() <= 0f)
            return worldAim;

        Vec3 relativeVelocity = clampVelocity(
                aimPointTarget.getDeltaMovement().subtract(mc.player.getDeltaMovement()));
        Vec3 predictedAim = worldAim.add(relativeVelocity.scale(prediction.getValue()));
        return isFinite(predictedAim) ? predictedAim : worldAim;
    }

    private Vec3 clampVelocity(Vec3 velocity) {
        if (!isFinite(velocity))
            return Vec3.ZERO;
        double length = velocity.length();
        if (length <= MAX_PREDICTION_VELOCITY)
            return velocity;
        return velocity.scale(MAX_PREDICTION_VELOCITY / length);
    }

    private Vec3 applyReactionDelay(Entity entity, Vec3 eyePosition,
                                    Vec3 predictedWorldAim, boolean targetChanged) {
        Vec3 targetPosition = entity.position();
        Vec3 targetVelocity = isFinite(entity.getDeltaMovement())
                ? entity.getDeltaMovement() : Vec3.ZERO;

        if (targetChanged || entity != reactionTarget) {
            reactionTarget = entity;
            lastObservedTargetPosition = targetPosition;
            lastObservedTargetVelocity = targetVelocity;
            acceptedWorldAim = predictedWorldAim;
            if (movementReaction.enabled) {
                startReaction(createHeldAim(eyePosition, predictedWorldAim));
            }
        } else {
            boolean repositioned = detectReposition(targetPosition);
            lastObservedTargetPosition = targetPosition;
            lastObservedTargetVelocity = targetVelocity;
            if (reactionCooldownTicks > 0)
                reactionCooldownTicks--;
            if (movementReaction.enabled && repositioned && !reactionHolding
                    && !reactionCatchingUp && reactionCooldownTicks <= 0) {
                startReaction(acceptedWorldAim != null ? acceptedWorldAim : predictedWorldAim);
            }
        }

        if (!movementReaction.enabled) {
            clearReactionState();
            reactionTarget = null;
            lastObservedTargetPosition = null;
            lastObservedTargetVelocity = null;
            acceptedWorldAim = predictedWorldAim;
            return predictedWorldAim;
        }

        if (reactionHolding) {
            if (System.nanoTime() < reactionUntilNanos)
                return aimFromRotation(eyePosition, heldYaw, heldPitch,
                        eyePosition.distanceTo(predictedWorldAim));
            reactionHolding = false;
            reactionCatchingUp = true;
            catchupWorldAim = aimFromRotation(eyePosition, heldYaw, heldPitch,
                    eyePosition.distanceTo(predictedWorldAim));
            catchupTicksRemaining = MAX_CATCHUP_TICKS;
            reactionCooldownTicks = REACTION_COOLDOWN_TICKS;
        }

        if (reactionCatchingUp) {
            double catchupFactor = Math.max(aimPointSmooth.getValue(), 0.25f);
            catchupWorldAim = catchupWorldAim.lerp(predictedWorldAim, catchupFactor);
            catchupTicksRemaining--;
            if (catchupTicksRemaining <= 0 || catchupWorldAim.distanceToSqr(predictedWorldAim) < 0.0004) {
                reactionCatchingUp = false;
                catchupWorldAim = predictedWorldAim;
            }
            acceptedWorldAim = catchupWorldAim;
            return catchupWorldAim;
        }

        acceptedWorldAim = predictedWorldAim;
        return predictedWorldAim;
    }

    private boolean detectReposition(Vec3 targetPosition) {
        if (lastObservedTargetPosition == null || lastObservedTargetVelocity == null
                || reactionHolding)
            return false;
        Vec3 residual = targetPosition.subtract(
                lastObservedTargetPosition.add(lastObservedTargetVelocity));
        double horizontalResidual = Math.sqrt(
                residual.x * residual.x + residual.z * residual.z);
        double threshold = repositionThreshold.getValue();
        return horizontalResidual > threshold || Math.abs(residual.y) > threshold * 1.5;
    }

    private void startReaction(Vec3 holdPoint) {
        int minDelay = reactionDelay.getMinValue();
        int maxDelay = reactionDelay.getMaxValue();
        int delayMillis = minDelay == maxDelay
                ? minDelay
                : ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
        heldWorldAim = holdPoint;
        Vec3 holdDelta = holdPoint.subtract(new Vec3(
                mc.player.getX(),
                mc.player.getY() + mc.player.getEyeHeight(),
                mc.player.getZ()));
        double horizontalDistance = Math.sqrt(
                holdDelta.x * holdDelta.x + holdDelta.z * holdDelta.z);
        heldYaw = (float) (Math.atan2(holdDelta.z, holdDelta.x) * 180 / Math.PI) - 90f;
        heldPitch = (float) -Math.toDegrees(Math.atan2(holdDelta.y, horizontalDistance));
        reactionUntilNanos = System.nanoTime() + delayMillis * 1_000_000L;
        reactionHolding = delayMillis > 0;
        reactionCatchingUp = false;
        catchupWorldAim = holdPoint;
        catchupTicksRemaining = 0;
    }

    private Vec3 createHeldAim(Vec3 eyePosition, Vec3 liveAim) {
        return aimFromRotation(eyePosition, serverYaw, serverPitch,
                Math.max(eyePosition.distanceTo(liveAim), 0.1));
    }

    private Vec3 aimFromRotation(Vec3 eyePosition, float yaw, float pitch, double distance) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 look = new Vec3(
                -Math.sin(yawRadians) * Math.cos(pitchRadians),
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * Math.cos(pitchRadians));
        return eyePosition.add(look.scale(Math.max(distance, 0.1)));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private void applyRotation(float targetYaw, float targetPitch, Entity target) {
        float rotYawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float rotPitchDiff = targetPitch - serverPitch;
        float speed = rotationSpeed.getValue();
        if (!rotationMode.is("Bezier")) {
            resetBezier();
        }

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
                float endpointYawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - smoothEndYaw));
                float endpointPitchDiff = Math.abs(targetPitch - smoothEndPitch);
                if (target != smoothTarget || smoothProgress >= 1f
                        || endpointYawDiff + endpointPitchDiff > SMOOTH_REPLAN_THRESHOLD) {
                    smoothTarget = target;
                    smoothStartYaw = serverYaw;
                    smoothStartPitch = serverPitch;
                    smoothEndYaw = targetYaw;
                    smoothEndPitch = targetPitch;
                    smoothProgress = 0f;
                }
                float totalYawDiff = MathHelper.wrapAngleTo180_float(smoothEndYaw - smoothStartYaw);
                float totalPitchDiff = smoothEndPitch - smoothStartPitch;
                float totalDist = Math.abs(totalYawDiff) + Math.abs(totalPitchDiff);
                if (totalDist <= 0.5f) {
                    serverYaw = smoothEndYaw;
                    serverPitch = smoothEndPitch;
                    smoothProgress = 1f;
                } else {
                    float duration = Math.max(totalDist / speed, 3f);
                    smoothProgress = Math.min(smoothProgress + 1f / duration, 1f);
                    float eased = applySmoothEasing(smoothProgress);
                    serverYaw = smoothStartYaw + totalYawDiff * eased;
                    serverPitch = smoothStartPitch + totalPitchDiff * eased;
                }
                break;
            }
            case "Bezier": {
                float endpointYawDiff = Math.abs(MathHelper.wrapAngleTo180_float(targetYaw - bezierEndYaw));
                float endpointPitchDiff = Math.abs(targetPitch - bezierEndPitch);
                if (target != bezierTarget || bezierProgress >= 1f
                        || endpointYawDiff + endpointPitchDiff > SMOOTH_REPLAN_THRESHOLD) {
                    planBezierSegment(targetYaw, targetPitch, target);
                }

                float totalYawDiff = bezierEndYaw - bezierStartYaw;
                float totalPitchDiff = bezierEndPitch - bezierStartPitch;
                float totalDist = (float) Math.hypot(totalYawDiff, totalPitchDiff);
                if (totalDist <= 0.5f) {
                    serverYaw = bezierEndYaw;
                    serverPitch = bezierEndPitch;
                    bezierProgress = 1f;
                } else {
                    float duration = Math.max(totalDist / speed, 3f);
                    bezierProgress = Math.min(bezierProgress + 1f / duration, 1f);
                    float eased = applyBaseEasing(bezierProgress);
                    serverYaw = cubicBezier(bezierStartYaw, bezierControl1Yaw,
                            bezierControl2Yaw, bezierEndYaw, eased);
                    serverPitch = cubicBezier(bezierStartPitch, bezierControl1Pitch,
                            bezierControl2Pitch, bezierEndPitch, eased);
                    if (bezierProgress >= 1f) {
                        serverYaw = bezierEndYaw;
                        serverPitch = bezierEndPitch;
                    }
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

    private void planBezierSegment(float targetYaw, float targetPitch, Entity target) {
        bezierTarget = target;
        bezierStartYaw = serverYaw;
        bezierStartPitch = serverPitch;

        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw);
        float pitchDiff = net.minecraft.util.Mth.clamp(targetPitch, -90f, 90f) - serverPitch;
        bezierEndYaw = serverYaw + yawDiff;
        bezierEndPitch = serverPitch + pitchDiff;
        bezierProgress = 0f;

        float distance = (float) Math.hypot(yawDiff, pitchDiff);
        if (distance <= 0.001f) {
            bezierControl1Yaw = bezierEndYaw;
            bezierControl1Pitch = bezierEndPitch;
            bezierControl2Yaw = bezierEndYaw;
            bezierControl2Pitch = bezierEndPitch;
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float control1Position = BEZIER_MIN_CONTROL_POSITION
                + random.nextFloat() * (BEZIER_MAX_CONTROL_POSITION - BEZIER_MIN_CONTROL_POSITION);
        float control2Position = 1f - (BEZIER_MIN_CONTROL_POSITION
                + random.nextFloat() * (BEZIER_MAX_CONTROL_POSITION - BEZIER_MIN_CONTROL_POSITION));

        float perpendicularYaw = -pitchDiff / distance;
        float perpendicularPitch = yawDiff / distance;
        float shortSegmentScale = net.minecraft.util.Mth.clamp(
                distance / BEZIER_SHORT_SEGMENT_SCALE, 0f, 1f);
        float maximumOffset = bezierRandomness.getValue()
                * Math.min(distance, BEZIER_CONTROL_DISTANCE_CAP) * shortSegmentScale;
        float control1Offset = centeredRandom(random, maximumOffset);
        float control2Offset = centeredRandom(random, maximumOffset);

        bezierControl1Yaw = bezierStartYaw + yawDiff * control1Position
                + perpendicularYaw * control1Offset;
        bezierControl1Pitch = net.minecraft.util.Mth.clamp(
                bezierStartPitch + pitchDiff * control1Position
                        + perpendicularPitch * control1Offset,
                -90f, 90f);
        bezierControl2Yaw = bezierStartYaw + yawDiff * control2Position
                + perpendicularYaw * control2Offset;
        bezierControl2Pitch = net.minecraft.util.Mth.clamp(
                bezierStartPitch + pitchDiff * control2Position
                        + perpendicularPitch * control2Offset,
                -90f, 90f);
    }

    private static float cubicBezier(float start, float control1, float control2, float end, float t) {
        float inverse = 1f - t;
        float inverseSquared = inverse * inverse;
        float tSquared = t * t;
        return inverseSquared * inverse * start
                + 3f * inverseSquared * t * control1
                + 3f * inverse * tSquared * control2
                + tSquared * t * end;
    }

    private float applyBaseEasing(float t) {
        return switch (easingCurve.get()) {
            case "EaseInOutCubic" -> SpringAnimation.easeInOutCubic(t);
            case "EaseOutCubic" -> SpringAnimation.easeOutCubic(t);
            case "EaseOutExpo" -> SpringAnimation.easeOutExpo(t);
            case "Smoothstep" -> t * t * (3f - 2f * t);
            default -> t;
        };
    }

    private float applySmoothEasing(float t) {
        float eased = applyBaseEasing(t);
        float overshootStrength = overshoot.getValue();
        if (overshootStrength > 0f) {
            float back = SpringAnimation.easeOutBack(t);
            eased += (back - eased) * overshootStrength;
        }
        return eased;
    }

    private void updateRotationJitter(Entity target, double distance) {
        float baseAmount = rotationJitter.getValue();
        if (baseAmount <= 0f) {
            resetJitter();
            return;
        }
        if (target != jitterTarget) {
            resetJitter();
            jitterTarget = target;
        }

        float normalizedDistance = net.minecraft.util.Mth.clamp(
                (float) (distance / Math.max(range.getValue(), 0.001f)), 0f, 1f);
        float amount = baseAmount * (1f
                + distanceSpread.getValue() * normalizedDistance * normalizedDistance);
        if (--jitterRefreshTicks <= 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            jitterTargetYaw = centeredRandom(random, amount);
            jitterTargetPitch = centeredRandom(random, amount * 0.65f);
            jitterRefreshTicks = random.nextInt(
                    JITTER_MIN_REFRESH_INTERVAL, JITTER_MAX_REFRESH_INTERVAL + 1);
        }
        jitterTargetYaw = net.minecraft.util.Mth.clamp(jitterTargetYaw, -amount, amount);
        jitterTargetPitch = net.minecraft.util.Mth.clamp(
                jitterTargetPitch, -amount * 0.65f, amount * 0.65f);
        jitterYaw += (jitterTargetYaw - jitterYaw) * JITTER_LERP_FACTOR;
        jitterPitch += (jitterTargetPitch - jitterPitch) * JITTER_LERP_FACTOR;
    }

    private float centeredRandom(ThreadLocalRandom random, float amount) {
        return ((random.nextFloat() + random.nextFloat()) - 1f) * amount;
    }

    private void resetRotationEffects() {
        smoothTarget = null;
        smoothProgress = 0f;
        smoothEndYaw = 0f;
        smoothEndPitch = 0f;
        resetBezier();
        if (mc.player != null) {
            serverYaw = mc.player.getYRot();
            serverPitch = mc.player.getXRot();
        }
        resetAimTracking();
        resetJitter();
    }

    private void resetBezier() {
        bezierTarget = null;
        bezierProgress = 0f;
        bezierStartYaw = 0f;
        bezierStartPitch = 0f;
        bezierControl1Yaw = 0f;
        bezierControl1Pitch = 0f;
        bezierControl2Yaw = 0f;
        bezierControl2Pitch = 0f;
        bezierEndYaw = 0f;
        bezierEndPitch = 0f;
    }

    private void resetAimTracking() {
        aimPointTarget = null;
        aimPointMode = null;
        currentLocalAim = null;
        goalLocalAim = null;
        randomAimTicksRemaining = 0;
        reactionTarget = null;
        lastObservedTargetPosition = null;
        lastObservedTargetVelocity = null;
        acceptedWorldAim = null;
        clearReactionState();
    }

    private void clearReactionState() {
        heldWorldAim = null;
        catchupWorldAim = null;
        heldYaw = 0f;
        heldPitch = 0f;
        reactionUntilNanos = 0L;
        reactionCooldownTicks = 0;
        catchupTicksRemaining = 0;
        reactionHolding = false;
        reactionCatchingUp = false;
    }

    private void resetJitter() {
        jitterTarget = null;
        jitterYaw = 0f;
        jitterPitch = 0f;
        jitterTargetYaw = 0f;
        jitterTargetPitch = 0f;
        jitterRefreshTicks = 0;
    }

    private boolean isLookingAtTarget(Entity entity) {
        if (mc.player == null || mc.level == null)
            return false;

        Vec3 start = mc.player.getEyePosition();
        Vec3 direction = Vec3.directionFromRotation(serverPitch, serverYaw);
        Vec3 end = start.add(direction.scale(range.getValue()));

        double maxDistanceSquared = start.distanceToSqr(end);
        if (blockRayTrace.enabled) {
            // 开启时由方块命中点截断射线，避免隔墙或透过方块攻击。
            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            maxDistanceSquared = start.distanceToSqr(blockHit.getLocation());
        }

        Entity closestHit = null;
        double closestDistanceSquared = maxDistanceSquared;
        for (Entity candidate : mc.level.entitiesForRendering()) {
            if (candidate == mc.player || !candidate.isPickable())
                continue;

            // 使用原始碰撞箱；只有真实命中实体时才允许攻击。
            Optional<Vec3> hit = candidate.getBoundingBox().clip(start, end);
            if (hit.isEmpty())
                continue;

            double hitDistanceSquared = start.distanceToSqr(hit.get());
            if (hitDistanceSquared < closestDistanceSquared) {
                closestDistanceSquared = hitDistanceSquared;
                closestHit = candidate;
            }
        }

        return closestHit == entity;
    }

    private boolean isTeammate(@UnknownNullability Entity player) {
        if (mc.player == null)
            return false;
        return player.getTeamColor() == mc.player.getTeamColor();
    }
}
