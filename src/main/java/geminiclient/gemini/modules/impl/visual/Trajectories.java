package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.TrajectoriesRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Trajectories module — renders predicted projectile arcs and projectile trails
 * using GLSL shaders instead of the legacy fixed-function GL pipeline.
 *
 * <p>Adapted from the LiquidBounce reference implementation.</p>
 */
public class Trajectories extends Module {

    // ── Configuration ──────────────────────────────────────────────

    private final BoolValue showSelf     = new BoolValue("Show Self", true);
    private final BoolValue showOthers   = new BoolValue("Show Others", false);
    private final ColorValue lineColor   = new ColorValue("Line Color", 0xFF00A0FF);
    private final BoolValue showTrails   = new BoolValue("Show Trails", true);
    private final IntValue  maxTrailSize = new IntValue("Max Trail", 20, 1, 100);

    // ── Trail state ────────────────────────────────────────────────

    private final Map<Entity, ArrayDeque<TrailPoint>> trailPositions = new HashMap<>();

    private record TrailPoint(Vec3 pos, long timestamp) {}

    // ── Trajectory result ──────────────────────────────────────────

    private record TrajectoryResult(List<Vec3> points, Vec3 landingPos, boolean hitEntity) {}

    // ── Constructor ────────────────────────────────────────────────

    public Trajectories() {
        super("Trajectories", ModuleEnum.Visual);
        addValue(showSelf, showOthers, lineColor, showTrails, maxTrailSize);
    }

    @Override
    public void onDisabled() {
        trailPositions.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Trail tracking (tick)
    // ═══════════════════════════════════════════════════════════════

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.level == null) return;

        long now = System.currentTimeMillis();

        // Collect all current projectile entities
        Set<Entity> seen = new HashSet<>();

        if (!(mc.level instanceof ClientLevel clientLevel)) return;

        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (!isTrackedProjectile(entity)) continue;

            seen.add(entity);
            ArrayDeque<TrailPoint> trail = trailPositions
                    .computeIfAbsent(entity, _ -> new ArrayDeque<>());

            // Remove very old points
            trail.removeIf(tp -> now - tp.timestamp > 10000);

            // Enforce max size
            while (trail.size() >= maxTrailSize.getValue()) {
                trail.removeFirst();
            }

            trail.addLast(new TrailPoint(entity.position(), now));
        }

        // Remove stale entities
        trailPositions.keySet().removeIf(e -> !seen.contains(e));

        // Fade trails for entities that are still in the map but gone from world
        // (handled by the iterator guard above)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Rendering (frame)
    // ═══════════════════════════════════════════════════════════════

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;

        // ── 1. Trajectory prediction for self and other entities ──
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player && !showSelf.enabled) continue;
            if (entity != mc.player && !showOthers.enabled) continue;

            TrajectoryResult result = calculateTrajectory(living);
            if (result != null) {
                renderTrajectory(event.poseStack(), result);
            }
        }

        // ── 2. Projectile trails ────────────────────────────────
        if (showTrails.enabled) {
            renderTrails(event.poseStack());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Physics — trajectory prediction
    // ═══════════════════════════════════════════════════════════════

    /**
     * Simulate the projectile trajectory for the given entity based on
     * its held item.  Returns {@code null} if the entity isn't holding
     * a valid projectile item.
     */
    private TrajectoryResult calculateTrajectory(LivingEntity entity) {
        ItemStack heldStack = entity.getMainHandItem();
        if (heldStack.isEmpty()) return null;

        Item item = heldStack.getItem();

        // ── Determine projectile parameters ────────────────────
        boolean isBow = false;
        float  motionFactor   = 1.5F;
        float  motionSlowdown = 0.99F;
        float  gravity;
        float  size;
        float  pitchOffset = 0f;

        if (item instanceof BowItem) {
            isBow  = true;
            gravity = 0.05F;
            size    = 0.3F;

            if (entity instanceof Player) {
                if (!entity.isUsingItem()) return null;
                float power = entity.getTicksUsingItem() / 20f;
                power = (power * power + power * 2f) / 3f;
                if (power < 0.1f) return null;
                if (power > 1f) power = 1f;
                motionFactor = power * 3f;
            } else {
                // Skeleton / other mob bow users
                motionFactor = 3f;
            }
        } else if (item instanceof FishingRodItem) {
            gravity         = 0.04F;
            size            = 0.25F;
            motionSlowdown  = 0.92F;
        } else if (item instanceof ThrowablePotionItem) {
            gravity         = 0.05F;
            size            = 0.25F;
            motionFactor    = 0.5F;
            pitchOffset     = -20f;
        } else if (item instanceof SnowballItem
                || item instanceof EnderpearlItem
                || item instanceof EggItem) {
            gravity         = 0.03F;
            size            = 0.25F;
        } else {
            return null; // Not a projectile item
        }

        // ── Launch position & velocity ─────────────────────────
        float yaw   = entity.getYRot();
        float pitch = entity.getXRot();

        double yawRad   = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch + pitchOffset);

        Vec3 pos = entity.position();

        double posX = pos.x - Math.cos(yawRad) * 0.16;
        double posY = pos.y + entity.getEyeHeight() - 0.1;
        double posZ = pos.z - Math.sin(yawRad) * 0.16;

        double motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * (isBow ? 1.0 : 0.4);
        double motionY = -Math.sin(pitchRad) * (isBow ? 1.0 : 0.4);
        double motionZ =  Math.cos(yawRad) * Math.cos(pitchRad) * (isBow ? 1.0 : 0.4);

        double dist = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (dist < 1e-7) return null;

        motionX = motionX / dist * motionFactor;
        motionY = motionY / dist * motionFactor;
        motionZ = motionZ / dist * motionFactor;

        // ── Simulate trajectory ────────────────────────────────
        List<Vec3> points       = new ArrayList<>();
        Vec3       landingPos   = null;
        boolean    hitEntity    = false;
        boolean    hasLanded    = false;

        // Safety limit to prevent infinite loops
        int maxSteps = 300;

        while (!hasLanded && posY > 0.0 && points.size() < maxSteps) {
            points.add(new Vec3(posX, posY, posZ));

            Vec3 posBefore = new Vec3(posX, posY, posZ);
            Vec3 posAfter  = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);

            // ── Block collision ─────────────────────────────────
            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    posBefore, posAfter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity));

            if (blockHit.getType() != HitResult.Type.MISS) {
                hasLanded  = true;
                landingPos = blockHit.getLocation();
                posAfter   = landingPos;
            }

            // ── Entity collision ────────────────────────────────
            AABB arrowBox = new AABB(
                    posX - size, posY - size, posZ - size,
                    posX + size, posY + size, posZ + size)
                    .expandTowards(motionX, motionY, motionZ)
                    .inflate(1.0);

            for (Entity possible : mc.level.getEntities(entity, arrowBox,
                    e -> e.canBeCollidedWith(null) && e != entity)) {

                AABB possibleBox = possible.getBoundingBox().inflate(size);
                Optional<Vec3> hit = possibleBox.clip(posBefore, posAfter);
                if (hit.isPresent()) {
                    hitEntity  = true;
                    hasLanded  = true;
                    landingPos = hit.get();
                    break;
                }
            }

            // ── Advance position ────────────────────────────────
            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            // ── Water drag ─────────────────────────────────────
            if (!mc.level.getFluidState(BlockPos.containing(posX, posY, posZ))
                    .is(Fluids.WATER)) {
                motionX *= motionSlowdown;
                motionY *= motionSlowdown;
                motionZ *= motionSlowdown;
            } else {
                motionX *= 0.6;
                motionY *= 0.6;
                motionZ *= 0.6;
            }

            motionY -= gravity;
        }

        // If we never hit anything, add the final simulated position
        if (!hasLanded && points.size() < maxSteps) {
            points.add(new Vec3(posX, posY, posZ));
            landingPos = new Vec3(posX, posY, posZ);
        }

        return new TrajectoryResult(points, landingPos, hitEntity);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Rendering helpers
    // ═══════════════════════════════════════════════════════════════

    private void renderTrajectory(PoseStack poseStack, TrajectoryResult result) {
        if (result.points == null || result.points.size() < 2) return;

        // Flatten points to float array
        float[] flat = new float[result.points.size() * 3];
        for (int i = 0; i < result.points.size(); i++) {
            Vec3 p = result.points.get(i);
            flat[i * 3]     = (float) p.x;
            flat[i * 3 + 1] = (float) p.y;
            flat[i * 3 + 2] = (float) p.z;
        }

        // Colors: start = configured, end = fading or red if entity hit
        int startRGBA = lineColor.getColor();  // ARGB from config

        int endRGBA;
        if (result.hitEntity) {
            endRGBA = 0x40FF0000; // Transparent red
        } else {
            // Fade the configured colour toward transparent
            int r = (startRGBA >> 16) & 0xFF;
            int g = (startRGBA >> 8)  & 0xFF;
            int b =  startRGBA        & 0xFF;
            endRGBA = (0x40 << 24) | (r << 16) | (g << 8) | b;
        }

        TrajectoriesRenderer.drawTrajectoryLine(poseStack, flat, result.points.size(),
                startRGBA, endRGBA);

        // Landing ring
        if (result.landingPos != null) {
            int ringColor = result.hitEntity ? 0x80FF0000 : 0x8000A0FF;
            TrajectoriesRenderer.drawLandingRing(poseStack,
                    result.landingPos.x, result.landingPos.y, result.landingPos.z,
                    ringColor);
        }
    }

    private void renderTrails(PoseStack poseStack) {
        long now = System.currentTimeMillis();

        for (Map.Entry<Entity, ArrayDeque<TrailPoint>> entry : trailPositions.entrySet()) {
            ArrayDeque<TrailPoint> trail = entry.getValue();
            if (trail.size() < 2) continue;

            int trailColor = getTrailColor(entry);

            // Build arrays from trail points
            int count = trail.size();
            float[] positions = new float[count * 3];
            int[]   colors    = new int[count];
            int idx = 0;
            for (TrailPoint tp : trail) {
                Vec3 p = tp.pos;
                positions[idx * 3]     = (float) p.x;
                positions[idx * 3 + 1] = (float) p.y;
                positions[idx * 3 + 2] = (float) p.z;

                // Fade alpha based on age
                long age = now - tp.timestamp;
                float alpha = 1.0f - Math.min(age / 3000f, 1.0f);
                int a = (int) (alpha * 255f);
                int r = (trailColor >> 16) & 0xFF;
                int g = (trailColor >> 8)  & 0xFF;
                int b =  trailColor        & 0xFF;
                colors[idx] = (a << 24) | (r << 16) | (g << 8) | b;

                idx++;
            }

            TrajectoriesRenderer.drawColoredLineStrip(poseStack, positions, colors, count);
        }
    }

    private static int getTrailColor(Map.Entry<Entity, ArrayDeque<TrailPoint>> entry) {
        Entity entity = entry.getKey();

        // Determine trail colour by entity type
        return switch (entity) {
            case Arrow ignored                        -> 0xFFFF0000;
            case AbstractThrownPotion ignored         -> 0xFFC89600;
            case ThrownEnderpearl ignored             -> 0xFFC800C8;
            case AbstractHurtingProjectile ignored    -> 0xFFFFFF00;
            case ThrownEgg ignored                    -> 0xFFC8FFC8;
            case Snowball ignored                     -> 0xFFC8FFC8;
            default                                   -> 0xFFFFFFFF;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  Type checks
    // ═══════════════════════════════════════════════════════════════

    private static boolean isTrackedProjectile(Entity entity) {
        return entity instanceof Arrow
                || entity instanceof AbstractThrownPotion
                || entity instanceof ThrownEnderpearl
                || entity instanceof ThrownExperienceBottle
                || entity instanceof Snowball
                || entity instanceof ThrownEgg
                || entity instanceof AbstractHurtingProjectile;
    }
}
