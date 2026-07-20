package geminiclient.gemini.modules.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.customRenderer.glsl.modules.TrajectoriesRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Two deliberately separate visual systems:
 * <ul>
 *     <li>Prediction: an obvious guide for a projectile that is still held.</li>
 *     <li>Flight FX: persistent, animated decoration for entities already launched.</li>
 * </ul>
 *
 * Rendering remains GPU based. Expensive point counts, particles and distance are
 * bounded by user-facing settings so the elaborate presets stay practical.
 */
public final class Trajectories extends Module {

    // Prediction visibility and filters
    private final BoolValue prediction = new BoolValue("Prediction", true);
    private final BoolValue showSelf = new BoolValue("Predict Self", true, () -> prediction.enabled);
    private final BoolValue showOthers = new BoolValue("Predict Others", false, () -> prediction.enabled);
    private final BoolValue predictBows = new BoolValue("Bows", true);
    private final BoolValue predictPearls = new BoolValue("Pearls", true);
    private final BoolValue predictThrowables = new BoolValue("Eggs & Snowballs", true);
    private final BoolValue predictPotions = new BoolValue("Potions", true);
    private final BoolValue predictRods = new BoolValue("Fishing Rods", true);
    private final BoolValue predictTridents = new BoolValue("Tridents", true);
    private final CheckboxValue predictionItems = new CheckboxValue("Prediction Items",
            new BoolValue[]{predictBows, predictPearls, predictThrowables,
                    predictPotions, predictRods, predictTridents},
            () -> prediction.enabled);

    // Prediction appearance
    private final ListValue predictionStyle = new ListValue("Guide Style", "Comet",
            new String[]{"Solid", "Dashed", "Comet"}, () -> prediction.enabled);
    private final ColorValue guideStartColor = new ColorValue("Guide Start", 0xFF72F6FF,
            () -> prediction.enabled);
    private final ColorValue guideEndColor = new ColorValue("Guide End", 0xFF9B62FF,
            () -> prediction.enabled);
    private final ColorValue entityHitColor = new ColorValue("Entity Hit", 0xFFFF496C,
            () -> prediction.enabled);
    private final FloatValue guideWidth = new FloatValue("Guide Width", 0.055f, 0.01f, 0.30f,
            () -> prediction.enabled);
    private final FloatValue guideOpacity = new FloatValue("Guide Opacity", 0.92f, 0.05f, 1.0f,
            () -> prediction.enabled);
    private final FloatValue guideGlow = new FloatValue("Guide Glow", 1.35f, 0.0f, 3.0f,
            () -> prediction.enabled);
    private final FloatValue guideFlowSpeed = new FloatValue("Guide Animation", 1.0f, 0.0f, 4.0f,
            () -> prediction.enabled);
    private final FloatValue dashDensity = new FloatValue("Dash Density", 11.0f, 2.0f, 30.0f,
            () -> prediction.enabled && predictionStyle.is("Dashed"));
    private final IntValue simulationSteps = new IntValue("Prediction Steps", 220, 40, 400,
            () -> prediction.enabled);

    // Landing indicator
    private final BoolValue landingMarker = new BoolValue("Landing Marker", true,
            () -> prediction.enabled);
    private final ListValue markerStyle = new ListValue("Marker Style", "Arcane",
            new String[]{"Ring", "Reticle", "Arcane"}, () -> prediction.enabled && landingMarker.enabled);
    private final FloatValue markerSize = new FloatValue("Marker Size", 0.72f, 0.2f, 2.5f,
            () -> prediction.enabled && landingMarker.enabled);
    private final FloatValue markerPulse = new FloatValue("Marker Pulse", 1.0f, 0.0f, 3.0f,
            () -> prediction.enabled && landingMarker.enabled);

    // Already-launched projectile filters
    private final BoolValue flightEffects = new BoolValue("Flight Effects", true);
    private final BoolValue fxArrows = new BoolValue("Arrows & Tridents", true);
    private final BoolValue fxPearls = new BoolValue("Ender Pearls", true);
    private final BoolValue fxThrowables = new BoolValue("Thrown Items", true);
    private final BoolValue fxPotions = new BoolValue("Thrown Potions", true);
    private final BoolValue fxMagic = new BoolValue("Magic Projectiles", true);
    private final BoolValue fxHooks = new BoolValue("Fishing Hooks", false);
    private final CheckboxValue flightItems = new CheckboxValue("Flight Entities",
            new BoolValue[]{fxArrows, fxPearls, fxThrowables, fxPotions, fxMagic, fxHooks},
            () -> flightEffects.enabled);

    // Flight trail
    private final BoolValue energyTrail = new BoolValue("Energy Ribbon", true,
            () -> flightEffects.enabled);
    private final ListValue trailColorMode = new ListValue("Trail Colors", "Entity",
            new String[]{"Entity", "Custom", "Rainbow"}, () -> flightEffects.enabled && energyTrail.enabled);
    private final ColorValue trailPrimary = new ColorValue("Trail Primary", 0xFF58E8FF,
            () -> flightEffects.enabled && energyTrail.enabled && trailColorMode.is("Custom"));
    private final ColorValue trailSecondary = new ColorValue("Trail Secondary", 0xFFFF62D4,
            () -> flightEffects.enabled && energyTrail.enabled && trailColorMode.is("Custom"));
    private final FloatValue trailWidth = new FloatValue("Ribbon Width", 0.105f, 0.015f, 0.50f,
            () -> flightEffects.enabled && energyTrail.enabled);
    private final FloatValue trailLifetime = new FloatValue("Trail Lifetime", 1.35f, 0.15f, 6.0f,
            () -> flightEffects.enabled && energyTrail.enabled);
    private final IntValue trailPoints = new IntValue("Trail Detail", 48, 8, 160,
            () -> flightEffects.enabled && energyTrail.enabled);
    private final FloatValue trailOpacity = new FloatValue("Trail Opacity", 0.88f, 0.05f, 1.0f,
            () -> flightEffects.enabled && energyTrail.enabled);
    private final FloatValue trailGlow = new FloatValue("Trail Glow", 1.65f, 0.0f, 3.0f,
            () -> flightEffects.enabled && energyTrail.enabled);

    // Decorative flight layers
    private final BoolValue headFlare = new BoolValue("Projectile Halo", true,
            () -> flightEffects.enabled);
    private final FloatValue flareSize = new FloatValue("Halo Size", 0.34f, 0.08f, 1.5f,
            () -> flightEffects.enabled && headFlare.enabled);
    private final FloatValue flareIntensity = new FloatValue("Halo Intensity", 1.25f, 0.1f, 3.0f,
            () -> flightEffects.enabled && headFlare.enabled);
    private final BoolValue trailSparks = new BoolValue("Trail Sparks", true,
            () -> flightEffects.enabled);
    private final IntValue sparkDensity = new IntValue("Spark Density", 3, 1, 8,
            () -> flightEffects.enabled && trailSparks.enabled);
    private final FloatValue sparkSize = new FloatValue("Spark Size", 0.095f, 0.025f, 0.35f,
            () -> flightEffects.enabled && trailSparks.enabled);
    private final BoolValue impactBurst = new BoolValue("Impact Burst", true,
            () -> flightEffects.enabled);
    private final FloatValue impactDuration = new FloatValue("Impact Duration", 0.75f, 0.15f, 2.5f,
            () -> flightEffects.enabled && impactBurst.enabled);
    private final FloatValue impactSize = new FloatValue("Impact Size", 1.15f, 0.25f, 4.0f,
            () -> flightEffects.enabled && impactBurst.enabled);

    // Global limits
    private final ListValue depthMode = new ListValue("Depth Mode", "Occluded",
            new String[]{"Occluded", "Through Walls"});
    private final FloatValue renderDistance = new FloatValue("Render Distance", 72.0f, 12.0f, 192.0f);

    private final Map<Integer, ProjectileTrack> projectileTracks = new HashMap<>();
    private final List<ImpactEffect> impacts = new ArrayList<>();

    private record TrailPoint(Vec3 pos, long timestamp) {}

    private static final class ProjectileTrack {
        private Entity entity;
        private final ProjectileKind kind;
        private final ArrayDeque<TrailPoint> points = new ArrayDeque<>();
        private long lastSeen;
        private boolean active = true;

        private ProjectileTrack(Entity entity, ProjectileKind kind, long now) {
            this.entity = entity;
            this.kind = kind;
            this.lastSeen = now;
        }
    }

    private record ImpactEffect(Vec3 pos, long timestamp, ProjectileKind kind) {}

    private record TrajectoryResult(List<Vec3> points, Vec3 landingPos, boolean hitEntity) {}

    private enum ProjectileKind {
        ARROW(0xFFFFB84D, 0xFFFF5A5F),
        PEARL(0xFFB86BFF, 0xFF4AFFF3),
        POTION(0xFFFF70D2, 0xFF8C62FF),
        THROWABLE(0xFFE9FAFF, 0xFF73CAFF),
        MAGIC(0xFFFFE85A, 0xFFFF6A2B),
        HOOK(0xFF82E6FF, 0xFFFFFFFF);

        private final int primary;
        private final int secondary;

        ProjectileKind(int primary, int secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    public Trajectories() {
        super("Trajectories", ModuleEnum.Visual);
        addValue(
                prediction, showSelf, showOthers, predictionItems,
                predictionStyle, guideStartColor, guideEndColor, entityHitColor,
                guideWidth, guideOpacity, guideGlow, guideFlowSpeed, dashDensity, simulationSteps,
                landingMarker, markerStyle, markerSize, markerPulse,
                flightEffects, flightItems, energyTrail, trailColorMode, trailPrimary, trailSecondary,
                trailWidth, trailLifetime, trailPoints, trailOpacity, trailGlow,
                headFlare, flareSize, flareIntensity, trailSparks, sparkDensity, sparkSize,
                impactBurst, impactDuration, impactSize,
                depthMode, renderDistance
        );
    }

    @Override
    public void onDisabled() {
        projectileTracks.clear();
        impacts.clear();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!(mc.level instanceof ClientLevel level) || mc.player == null) {
            projectileTracks.clear();
            impacts.clear();
            return;
        }

        if (!flightEffects.enabled) {
            projectileTracks.clear();
            impacts.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long trailLifeMs = Math.max(1L, (long) (trailLifetime.getValue() * 1000.0f));
        Set<Integer> seen = new HashSet<>();

        for (Entity entity : level.entitiesForRendering()) {
            ProjectileKind kind = classifyProjectile(entity);
            if (kind == null || !flightKindEnabled(kind)) continue;

            int id = entity.getId();
            seen.add(id);
            if (!withinRenderDistance(entity.position())) continue;

            ProjectileTrack track = projectileTracks.get(id);
            if (track == null || track.entity != entity || track.kind != kind) {
                track = new ProjectileTrack(entity, kind, now);
                projectileTracks.put(id, track);
            }

            track.entity = entity;
            track.active = true;
            track.lastSeen = now;
            pruneTrail(track.points, now, trailLifeMs);

            Vec3 position = entity.position();
            TrailPoint newest = track.points.peekLast();
            if (newest == null || newest.pos.distanceToSqr(position) > 0.0004) {
                while (track.points.size() >= trailPoints.getValue()) track.points.removeFirst();
                track.points.addLast(new TrailPoint(position, now));
            }
        }

        Iterator<Map.Entry<Integer, ProjectileTrack>> iterator = projectileTracks.entrySet().iterator();
        while (iterator.hasNext()) {
            ProjectileTrack track = iterator.next().getValue();
            pruneTrail(track.points, now, trailLifeMs);

            if (track.active && !seen.contains(track.entity.getId())) {
                track.active = false;
                TrailPoint last = track.points.peekLast();
                if (impactBurst.enabled && last != null && track.points.size() >= 2) {
                    impacts.add(new ImpactEffect(last.pos, now, track.kind));
                }
            }

            if (!track.active && track.points.isEmpty()) iterator.remove();
        }

        long impactLifeMs = Math.max(1L, (long) (impactDuration.getValue() * 1000.0f));
        impacts.removeIf(impact -> now - impact.timestamp > impactLifeMs);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        if (prediction.enabled) renderPredictions(event.poseStack(), event.partialTick());
        if (flightEffects.enabled) renderFlightEffects(event.poseStack());
    }

    private void renderPredictions(PoseStack poseStack, float partialTick) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player && !showSelf.enabled) continue;
            if (entity != mc.player && !showOthers.enabled) continue;
            if (!withinRenderDistance(entity.getPosition(partialTick))) continue;

            TrajectoryResult result = calculateTrajectory(living, partialTick);
            if (result != null) renderPrediction(poseStack, result);
        }
    }

    private void renderPrediction(PoseStack poseStack, TrajectoryResult result) {
        if (result.points.size() < 2) return;

        float[] positions = flatten(result.points);
        int start = withAlpha(guideStartColor.getColor(), guideOpacity.getValue());
        int end = withAlpha(result.hitEntity ? entityHitColor.getColor() : guideEndColor.getColor(),
                guideOpacity.getValue());
        float time = System.currentTimeMillis() * 0.001f * guideFlowSpeed.getValue();
        int style = predictionStyle.is("Solid") ? 0 : predictionStyle.is("Dashed") ? 1 : 2;
        boolean xray = depthMode.is("Through Walls");

        if (guideGlow.getValue() > 0.01f) {
            int glowStart = scaleAlpha(start, 0.20f * guideGlow.getValue());
            int glowEnd = scaleAlpha(end, 0.20f * guideGlow.getValue());
            TrajectoriesRenderer.drawRibbon(poseStack, positions, result.points.size(),
                    glowStart, glowEnd, guideWidth.getValue() * (2.2f + guideGlow.getValue()),
                    style, dashDensity.getValue(), time, xray);
        }

        TrajectoriesRenderer.drawRibbon(poseStack, positions, result.points.size(),
                start, end, guideWidth.getValue(), style, dashDensity.getValue(), time, xray);

        if (landingMarker.enabled && result.landingPos != null) {
            float pulse = 1.0f + 0.10f * markerPulse.getValue()
                    * (float) Math.sin(System.currentTimeMillis() * 0.006 * markerPulse.getValue());
            int markerColor = result.hitEntity ? entityHitColor.getColor() : guideEndColor.getColor();
            int markerType = markerStyle.is("Ring") ? 0 : markerStyle.is("Reticle") ? 1 : 2;
            TrajectoriesRenderer.drawMarker(poseStack, result.landingPos,
                    markerSize.getValue() * pulse, withAlpha(markerColor, guideOpacity.getValue()),
                    markerType, time, xray);
        }
    }

    private void renderFlightEffects(PoseStack poseStack) {
        long now = System.currentTimeMillis();
        long lifeMs = Math.max(1L, (long) (trailLifetime.getValue() * 1000.0f));
        boolean xray = depthMode.is("Through Walls");

        for (ProjectileTrack track : projectileTracks.values()) {
            if (track.points.isEmpty()) continue;
            TrailPoint newest = track.points.peekLast();
            if (newest == null || !withinRenderDistance(newest.pos)) continue;

            List<TrailPoint> points = new ArrayList<>(track.points);
            int count = points.size();
            float[] positions = new float[count * 3];
            int[] colors = new int[count];

            for (int i = 0; i < count; i++) {
                TrailPoint point = points.get(i);
                positions[i * 3] = (float) point.pos.x;
                positions[i * 3 + 1] = (float) point.pos.y;
                positions[i * 3 + 2] = (float) point.pos.z;

                float ageAlpha = 1.0f - clamp01((now - point.timestamp) / (float) lifeMs);
                float lengthAlpha = (i + 1f) / count;
                int color = trailColor(track.kind, i / (float) Math.max(1, count - 1), now);
                colors[i] = withAlpha(color, ageAlpha * lengthAlpha * trailOpacity.getValue());
            }

            if (energyTrail.enabled && count >= 2) {
                if (trailGlow.getValue() > 0.01f) {
                    int[] glowColors = new int[count];
                    for (int i = 0; i < count; i++) {
                        glowColors[i] = scaleAlpha(colors[i], 0.18f * trailGlow.getValue());
                    }
                    TrajectoriesRenderer.drawColoredRibbon(poseStack, positions, glowColors, count,
                            trailWidth.getValue() * (2.0f + trailGlow.getValue()), xray);
                }
                TrajectoriesRenderer.drawColoredRibbon(poseStack, positions, colors, count,
                        trailWidth.getValue(), xray);
            }

            if (trailSparks.enabled && count > 1) {
                int spacing = Math.max(1, 9 - sparkDensity.getValue());
                for (int i = (track.entity == null ? 0 : track.entity.getId()) % spacing;
                     i < count - 1; i += spacing) {
                    float flicker = 0.65f + 0.35f
                            * (float) Math.sin(now * 0.012 + i * 2.17);
                    TrajectoriesRenderer.drawFlare(poseStack, points.get(i).pos,
                            sparkSize.getValue() * flicker, colors[i], now * 0.001f + i, xray);
                }
            }

            if (headFlare.enabled && track.active && track.entity != null) {
                int headColor = trailColor(track.kind, 1.0f, now);
                float speed = (float) track.entity.getDeltaMovement().length();
                float scale = flareSize.getValue() * (1.0f + Math.min(speed, 2.0f) * 0.22f);
                int color = withAlpha(headColor, clamp01(0.55f * flareIntensity.getValue()));
                TrajectoriesRenderer.drawFlare(poseStack, track.entity.position(), scale, color,
                        now * 0.001f, xray);
            }
        }

        if (impactBurst.enabled) {
            float durationMs = Math.max(1.0f, impactDuration.getValue() * 1000.0f);
            for (ImpactEffect impact : impacts) {
                if (!withinRenderDistance(impact.pos)) continue;
                float progress = clamp01((now - impact.timestamp) / durationMs);
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                float alpha = (1.0f - progress) * (1.0f - progress);
                int primary = withAlpha(trailColor(impact.kind, progress, now), alpha);
                int secondary = withAlpha(impact.kind.secondary, alpha * 0.75f);
                float size = impactSize.getValue() * (0.20f + eased);

                TrajectoriesRenderer.drawMarker(poseStack, impact.pos, size, primary,
                        2, now * 0.001f, xray);
                TrajectoriesRenderer.drawFlare(poseStack, impact.pos, size * 0.72f,
                        secondary, now * 0.001f, xray);
            }
        }
    }

    private TrajectoryResult calculateTrajectory(LivingEntity entity, float partialTick) {
        ItemStack heldStack = entity.isUsingItem() ? entity.getUseItem() : entity.getMainHandItem();
        if (heldStack.isEmpty()) heldStack = entity.getOffhandItem();
        if (heldStack.isEmpty()) return null;

        Item item = heldStack.getItem();
        boolean directAim = false;
        float motionFactor = 1.5f;
        float motionSlowdown = 0.99f;
        float gravity;
        float size;
        float pitchOffset = 0.0f;

        if (item instanceof BowItem) {
            if (!predictBows.enabled || !entity.isUsingItem()) return null;
            directAim = true;
            gravity = 0.05f;
            size = 0.3f;
            if (entity instanceof Player) {
                float power = entity.getTicksUsingItem() / 20.0f;
                power = (power * power + power * 2.0f) / 3.0f;
                if (power < 0.1f) return null;
                motionFactor = Math.min(power, 1.0f) * 3.0f;
            } else {
                motionFactor = 3.0f;
            }
        } else if (item instanceof TridentItem) {
            if (!predictTridents.enabled || !entity.isUsingItem()
                    || entity.getTicksUsingItem() < 10) return null;
            directAim = true;
            gravity = 0.05f;
            size = 0.3f;
            motionFactor = 2.5f;
        } else if (item instanceof FishingRodItem) {
            if (!predictRods.enabled) return null;
            gravity = 0.04f;
            size = 0.25f;
            motionSlowdown = 0.92f;
        } else if (item instanceof ThrowablePotionItem) {
            if (!predictPotions.enabled) return null;
            gravity = 0.05f;
            size = 0.25f;
            motionFactor = 0.5f;
            pitchOffset = -20.0f;
        } else if (item instanceof EnderpearlItem) {
            if (!predictPearls.enabled) return null;
            gravity = 0.03f;
            size = 0.25f;
        } else if (item instanceof SnowballItem || item instanceof EggItem) {
            if (!predictThrowables.enabled) return null;
            gravity = 0.03f;
            size = 0.25f;
        } else {
            return null;
        }

        // Camera and world rendering use the same interpolated frame time. Starting
        // from raw tick coordinates makes this guide jump under fast movement and
        // temporal AA then preserves several old positions as parallel "ghost" lines.
        float yaw = entity.getViewYRot(partialTick);
        float pitch = entity.getViewXRot(partialTick);
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch + pitchOffset);
        Vec3 base = entity.getPosition(partialTick);

        double posX = base.x - Math.cos(yawRad) * 0.16;
        double posY = base.y + entity.getEyeHeight() - 0.1;
        double posZ = base.z - Math.sin(yawRad) * 0.16;
        double aimScale = directAim ? 1.0 : 0.4;
        double motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * aimScale;
        double motionY = -Math.sin(pitchRad) * aimScale;
        double motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * aimScale;
        double distance = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (distance < 1.0e-7) return null;

        motionX = motionX / distance * motionFactor;
        motionY = motionY / distance * motionFactor;
        motionZ = motionZ / distance * motionFactor;

        List<Vec3> points = new ArrayList<>();
        Vec3 landingPos = null;
        boolean hitEntity = false;
        boolean landed = false;
        int maxSteps = simulationSteps.getValue();

        while (!landed && posY > mc.level.getMinY() - 8.0 && points.size() < maxSteps) {
            Vec3 before = new Vec3(posX, posY, posZ);
            Vec3 after = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            points.add(before);

            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    before, after, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (blockHit.getType() != HitResult.Type.MISS) {
                landed = true;
                landingPos = blockHit.getLocation();
                after = landingPos;
            }

            AABB sweptBox = new AABB(
                    posX - size, posY - size, posZ - size,
                    posX + size, posY + size, posZ + size)
                    .expandTowards(motionX, motionY, motionZ)
                    .inflate(1.0);

            for (Entity possible : mc.level.getEntities(entity, sweptBox,
                    possible -> possible.canBeCollidedWith(null) && possible != entity)) {
                Optional<Vec3> hit = possible.getBoundingBox().inflate(size).clip(before, after);
                if (hit.isPresent()) {
                    hitEntity = true;
                    landed = true;
                    landingPos = hit.get();
                    break;
                }
            }

            if (landed && landingPos != null) {
                points.add(landingPos);
                break;
            }

            posX = after.x;
            posY = after.y;
            posZ = after.z;
            if (mc.level.getFluidState(BlockPos.containing(posX, posY, posZ)).is(Fluids.WATER)) {
                motionX *= 0.6;
                motionY *= 0.6;
                motionZ *= 0.6;
            } else {
                motionX *= motionSlowdown;
                motionY *= motionSlowdown;
                motionZ *= motionSlowdown;
            }
            motionY -= gravity;
        }

        if (!landed) {
            landingPos = new Vec3(posX, posY, posZ);
            if (points.isEmpty() || points.getLast().distanceToSqr(landingPos) > 1.0e-6) {
                points.add(landingPos);
            }
        }

        return points.size() >= 2 ? new TrajectoryResult(points, landingPos, hitEntity) : null;
    }

    private ProjectileKind classifyProjectile(Entity entity) {
        if (entity instanceof AbstractArrow || entity instanceof Arrow) return ProjectileKind.ARROW;
        if (entity instanceof ThrownEnderpearl) return ProjectileKind.PEARL;
        if (entity instanceof AbstractThrownPotion) return ProjectileKind.POTION;
        if (entity instanceof Snowball || entity instanceof ThrownEgg
                || entity instanceof ThrownExperienceBottle) return ProjectileKind.THROWABLE;
        if (entity instanceof AbstractHurtingProjectile) return ProjectileKind.MAGIC;
        if (entity instanceof FishingHook) return ProjectileKind.HOOK;
        return null;
    }

    private boolean flightKindEnabled(ProjectileKind kind) {
        return switch (kind) {
            case ARROW -> fxArrows.enabled;
            case PEARL -> fxPearls.enabled;
            case POTION -> fxPotions.enabled;
            case THROWABLE -> fxThrowables.enabled;
            case MAGIC -> fxMagic.enabled;
            case HOOK -> fxHooks.enabled;
        };
    }

    private int trailColor(ProjectileKind kind, float progress, long now) {
        if (trailColorMode.is("Custom")) {
            return blendColor(trailPrimary.getColor(), trailSecondary.getColor(), progress);
        }
        if (trailColorMode.is("Rainbow")) {
            float hue = (now * 0.00012f + progress * 0.72f) % 1.0f;
            return hsv(hue, 0.68f, 1.0f);
        }
        return blendColor(kind.primary, kind.secondary, progress);
    }

    private boolean withinRenderDistance(Vec3 position) {
        if (mc.player == null) return false;
        double max = renderDistance.getValue();
        return mc.player.position().distanceToSqr(position) <= max * max;
    }

    private static void pruneTrail(ArrayDeque<TrailPoint> trail, long now, long lifeMs) {
        while (!trail.isEmpty() && now - trail.peekFirst().timestamp > lifeMs) {
            trail.removeFirst();
        }
    }

    private static float[] flatten(List<Vec3> points) {
        float[] flattened = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            flattened[i * 3] = (float) point.x;
            flattened[i * 3 + 1] = (float) point.y;
            flattened[i * 3 + 2] = (float) point.z;
        }
        return flattened;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255.0f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaleAlpha(int argb, float scale) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        return withAlpha(argb, alpha * scale);
    }

    private static int blendColor(int first, int second, float amount) {
        float t = clamp01(amount);
        int r = Math.round(((first >> 16) & 0xFF) * (1.0f - t) + ((second >> 16) & 0xFF) * t);
        int g = Math.round(((first >> 8) & 0xFF) * (1.0f - t) + ((second >> 8) & 0xFF) * t);
        int b = Math.round((first & 0xFF) * (1.0f - t) + (second & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) h;
        float fraction = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * fraction);
        float t = value * (1.0f - saturation * (1.0f - fraction));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return 0xFF000000
                | (Math.round(r * 255.0f) << 16)
                | (Math.round(g * 255.0f) << 8)
                | Math.round(b * 255.0f);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
