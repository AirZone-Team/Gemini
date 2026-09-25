package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.CustomRendererRegistry;
import geminiclient.gemini.customRenderer.glsl.modules.BlackHoleDust;
import geminiclient.gemini.customRenderer.glsl.modules.BlackHoleFollow;
import geminiclient.gemini.customRenderer.glsl.modules.BlackHolePetRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.FrameEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * A black hole that rides the player's shoulder as a pet.
 *
 * <p>The hole never sits on the shoulder rigidly. Its anchor is a point beside
 * the neck, but the hole itself chases that anchor through a damped spring and
 * its accretion disk carries its own angular momentum, so a hard turn leaves it
 * swinging wide behind you and it settles back into place once you stop. Its
 * brightness answers the same chase: dragged along at speed, it feeds harder and
 * lights up.</p>
 *
 * <p>Everything drawn here is world geometry except the lens pass, which is the
 * point of the whole thing: the frame the level renderer already produced is
 * bent around the hole, so the far rim of the 3D disk folds over the shadow the
 * way light actually arrives from behind a Schwarzschild metric.</p>
 */
public class BlackHolePet extends Module {

    /** Motes the dust field can hold; {@code moteCount} decides how many run. */
    private static final int MOTE_CAPACITY = 200;

    /** Radius, in Schwarzschild radii, where a mote is swallowed. */
    private static final float MOTE_CAPTURE_RADIUS = 1.35f;

    // Placement on the player
    private final ListValue shoulder = new ListValue("Shoulder", "Left",
            new String[]{"Left", "Right"});
    private final FloatValue followSpeed = new FloatValue("Follow Speed", 2.0f, 0.1f, 3.0f);
    private final FloatValue swing = new FloatValue("Swing", 0.4f, 0.0f, 1.0f);
    private final FloatValue height = new FloatValue("Height", 1.32f, 0.4f, 2.4f);
    private final FloatValue sideOffset = new FloatValue("Side Offset", 0.46f, 0.05f, 1.2f);
    private final FloatValue forwardOffset = new FloatValue("Forward Offset", -0.02f, -0.7f, 0.7f);
    private final FloatValue orbitRadius = new FloatValue("Idle Orbit", 0.06f, 0.0f, 0.4f);
    private final FloatValue orbitSpeed = new FloatValue("Orbit Speed", 0.85f, 0.0f, 3.0f);
    private final FloatValue bob = new FloatValue("Bob", 0.045f, 0.0f, 0.3f);

    // The hole itself
    private final FloatValue size = new FloatValue("Size", 0.085f, 0.02f, 0.3f);
    private final FloatValue diskScale = new FloatValue("Disk Scale", 4.6f, 1.6f, 9.0f);
    private final FloatValue diskTilt = new FloatValue("Disk Tilt", 17.0f, 0.0f, 75.0f);
    private final FloatValue spinSpeed = new FloatValue("Spin Speed", 1.25f, 0.0f, 4.0f);

    // Art direction
    private final ListValue palette = new ListValue("Palette", "Gargantua",
            new String[]{"Gargantua", "Plasma", "Ice", "Void", "Spectrum", "Custom"});
    private final ColorValue hotColor = new ColorValue("Primary", 0xFFFFE7C4,
            () -> palette.is("Custom"));
    private final ColorValue coolColor = new ColorValue("Secondary", 0xFFB03A0E,
            () -> palette.is("Custom"));
    private final ColorValue tintColor = new ColorValue("Accent", 0xFF9FC4FF);
    private final FloatValue brightness = new FloatValue("Brightness", 1.15f, 0.1f, 3.0f);
    private final FloatValue opacity = new FloatValue("Opacity", 0.95f, 0.1f, 1.0f);
    private final FloatValue doppler = new FloatValue("Doppler Beaming", 0.8f, 0.0f, 1.0f);
    private final FloatValue redshift = new FloatValue("Grav Redshift", 0.6f, 0.0f, 1.0f);
    private final FloatValue noiseScale = new FloatValue("Filament Scale", 1.0f, 0.2f, 3.0f);
    private final FloatValue turbulence = new FloatValue("Turbulence", 0.9f, 0.0f, 1.6f);

    // Layers
    private final BoolValue drawDisk = new BoolValue("Accretion Disk", true);
    private final BoolValue drawArc = new BoolValue("Lensed Arc", true, () -> drawDisk.enabled);
    private final BoolValue drawJets = new BoolValue("Relativistic Jets", true);
    private final FloatValue jetLength = new FloatValue("Jet Length", 3.4f, 0.0f, 9.0f,
            () -> drawJets.enabled);
    private final BoolValue drawMotes = new BoolValue("Infall Motes", true);
    private final IntValue moteCount = new IntValue("Mote Count", 90, 0, MOTE_CAPACITY,
            () -> drawMotes.enabled);

    // Bending the frame around it
    private final BoolValue lensing = new BoolValue("Gravitational Lensing", true);
    private final FloatValue lensStrength = new FloatValue("Lens Strength", 0.6f, 0.0f, 1.5f,
            () -> lensing.enabled);
    private final FloatValue photonRing = new FloatValue("Photon Ring", 0.75f, 0.0f, 1.5f,
            () -> lensing.enabled);
    private final FloatValue frameDrag = new FloatValue("Frame Drag", 0.35f, 0.0f, 1.2f,
            () -> lensing.enabled);
    private final FloatValue capture = new FloatValue("Light Capture", 0.5f, 0.0f, 1.0f,
            () -> lensing.enabled);

    private final BlackHoleFollow follow = new BlackHoleFollow();
    private final BlackHoleDust dust = new BlackHoleDust(MOTE_CAPACITY);
    private final float[] basis = new float[9];

    /** Integrated disk rotation, so moving the spin slider never snaps the pattern. */
    private float diskPhase;
    private float flare;

    /**
     * Origin of the rig's float coordinate frame, in world blocks. The chase is
     * integrated in floats, which would quantise to a few millimetres once a
     * player is far enough from the origin, so the frame travels with them and
     * the pet only ever carries a small offset.
     */
    private double refX;
    private double refY;
    private double refZ;
    private boolean referenced;

    /** Where the hole was drawn this frame, in world blocks. */
    private double petX;
    private double petY;
    private double petZ;

    /** Anchor motion, blocks/s, estimated across frames and used to lead it. */
    private double lastAnchorX;
    private double lastAnchorY;
    private double lastAnchorZ;
    private float anchorVelX;
    private float anchorVelY;
    private float anchorVelZ;

    /** Snapshot of where the hole was drawn, consumed by the lens pass. */
    private boolean lensArmed;
    private float lensShadow;

    public BlackHolePet() {
        super("BlackHolePet", ModuleEnum.Visual);
        addValue(
                shoulder, followSpeed, swing, height, sideOffset, forwardOffset,
                orbitRadius, orbitSpeed, bob,
                size, diskScale, diskTilt, spinSpeed,
                palette, hotColor, coolColor, tintColor,
                brightness, opacity, doppler, redshift, noiseScale, turbulence,
                drawDisk, drawArc, drawJets, jetLength, drawMotes, moteCount,
                lensing, lensStrength, photonRing, frameDrag, capture
        );
    }

    @Override
    public void onEnabled() {
        follow.reset();
        referenced = false;
        lensArmed = false;
        diskPhase = 0f;
        flare = 0f;
        dust.reseed(moteCount.getValue());
    }

    @Override
    public void onDisabled() {
        lensArmed = false;
        referenced = false;
        follow.reset();
    }

    /**
     * Advance the chase once per rendered frame. Frame time rather than tick
     * time, because a pet that only updated at 20 TPS would visibly ratchet
     * along its own spring.
     */
    @EventTarget
    public void onFrame(FrameEvent event) {
        if (mc.player == null || mc.level == null) {
            follow.reset();
            referenced = false;
            return;
        }

        float deltaTime = Math.min(event.realTimeDeltaTicks() / 20.0f, 0.25f);
        Vec3 anchor = shoulderAnchor(event.partialTick());

        if (!referenced) {
            reference(anchor);
            follow.snapTo(0f, 0f, 0f);
        } else if (follow.distanceTo((float) (anchor.x - refX), (float) (anchor.y - refY),
                (float) (anchor.z - refZ)) > BlackHoleFollow.MAX_GRAB_DISTANCE
                || outOfRange(anchor)) {
            // Teleport, dimension change or a long run from the last reference:
            // re-anchor under the pet instead of letting it chase across a map.
            reference(anchor);
            follow.snapTo(0f, 0f, 0f);
        }

        // A spring chasing a moving anchor always trails it by v·c/k, which at
        // sprint speed is over half a block — that reads as "floating behind
        // you", not "on your shoulder". Leading the target by exactly that much
        // cancels the cruising error and keeps the lag where it is worth seeing:
        // on starts, turns and stops.
        updateAnchorVelocity(anchor, deltaTime);

        // A critically damped spring would be boring: the point of the rig is
        // that the hole arrives late and overshoots, so the ratio is exposed as
        // Swing and the player decides how much lag the pet carries.
        float stiffness = 120f * followSpeed.getValue() * followSpeed.getValue();
        float damping = (1.0f - swing.getValue() * 0.8f) * 2.0f * (float) Math.sqrt(stiffness);
        float lead = damping / stiffness;
        float yawRadians = (float) Math.toRadians(
                mc.player.getPreciseBodyRotation(event.partialTick()));

        follow.step(deltaTime,
                (float) (anchor.x - refX) + anchorVelX * lead,
                (float) (anchor.y - refY) + anchorVelY * lead,
                (float) (anchor.z - refZ) + anchorVelZ * lead,
                stiffness, damping,
                yawRadians, stiffness * 0.3f, (float) Math.sqrt(stiffness) * 1.45f,
                (float) Math.toRadians(diskTilt.getValue()), 26.0f, 8.0f,
                orbitSpeed.getValue());
        follow.basis(basis);

        diskPhase += spinSpeed.getValue() * deltaTime;

        float chase = follow.chaseSpeed();
        float anchorSpeed = (float) Math.sqrt(
                anchorVelX * anchorVelX + anchorVelY * anchorVelY + anchorVelZ * anchorVelZ);
        float wanted = Math.clamp(chase / 5.0f + follow.strain * 0.5f + anchorSpeed / 16.0f, 0f, 1f);
        flare += (wanted - flare) * Math.min(1f, deltaTime * 5.0f);

        int wantedMotes = drawMotes.enabled ? moteCount.getValue() : 0;
        if (wantedMotes != dust.count) dust.reseed(wantedMotes);
        if (dust.count > 0) {
            // The same ω ∝ n^-3/2 the disk shader shears its filaments with, so
            // the motes and the plasma they streak through turn together.
            dust.step(deltaTime, size.getValue(), spinSpeed.getValue(),
                    MOTE_CAPTURE_RADIUS, diskScale.getValue(), basis);
        }
    }

    private void reference(Vec3 anchor) {
        refX = anchor.x;
        refY = anchor.y;
        refZ = anchor.z;
        referenced = true;
        anchorVelX = anchorVelY = anchorVelZ = 0f;
        lastAnchorX = anchor.x;
        lastAnchorY = anchor.y;
        lastAnchorZ = anchor.z;
    }

    /**
     * Smoothed finite difference of the anchor. A single long frame must not be
     * read as a sprint, so the estimate is bled in over a few frames rather than
     * taken raw.
     */
    private void updateAnchorVelocity(Vec3 anchor, float deltaTime) {
        float inv = 1f / Math.max(deltaTime, 1e-4f);
        float blend = Math.min(1f, deltaTime * 8f);
        anchorVelX += ((float) (anchor.x - lastAnchorX) * inv - anchorVelX) * blend;
        anchorVelY += ((float) (anchor.y - lastAnchorY) * inv - anchorVelY) * blend;
        anchorVelZ += ((float) (anchor.z - lastAnchorZ) * inv - anchorVelZ) * blend;
        lastAnchorX = anchor.x;
        lastAnchorY = anchor.y;
        lastAnchorZ = anchor.z;
    }

    /**
     * True once the shoulder has travelled far enough from the reference that
     * the float offsets would start losing precision, which on a large server
     * coordinate is well before it is visible.
     */
    private boolean outOfRange(Vec3 anchor) {
        double dx = anchor.x - refX;
        double dy = anchor.y - refY;
        double dz = anchor.z - refZ;
        return dx * dx + dy * dy + dz * dz > 65536.0;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !referenced) return;
        // Submitting a custom pipeline before ShaderManager publishes gemini's
        // sources caches an invalid pipeline for the rest of the session.
        if (!CustomRendererRegistry.areShadersReady()) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        petX = refX + follow.x;
        petY = refY + follow.y;
        petZ = refZ + follow.z;

        float rs = size.getValue();
        var position = camera.position();
        BlackHolePetRenderer.Settings settings = new BlackHolePetRenderer.Settings(
                diskPhase,
                0.6f,
                palette.index,
                1.0f,
                flare,
                (float) (petX - position.x),
                (float) (petY - position.y),
                (float) (petZ - position.z),
                rs,
                rs * 1.42f,
                rs * diskScale.getValue(),
                rs * 0.62f,
                rs * 1.32f,
                1.62f,
                rs * jetLength.getValue(),
                rs * 0.09f,
                noiseScale.getValue(),
                turbulence.getValue(),
                redshift.getValue(),
                doppler.getValue(),
                opacity.getValue(),
                brightness.getValue(),
                hotColor.getColor(),
                coolColor.getColor(),
                tintColor.getColor(),
                basis,
                true,
                drawDisk.enabled,
                drawArc.enabled && drawDisk.enabled,
                drawJets.enabled
        );

        BlackHolePetRenderer.draw(event.poseStack(), settings, drawMotes.enabled ? dust : null);

        lensArmed = lensing.enabled && lensStrength.getValue() > 0.001f;
        // The apparent shadow is a little wider than the horizon sphere itself;
        // matching the geometry keeps the frame pass from cutting a second disc.
        lensShadow = rs * 1.05f;
    }

    /**
     * Bend the frame around the hole. Called from the post-processing hook,
     * after the world has rendered and before the GUI, in the same frame as the
     * {@link #onRender3D} that armed it.
     */
    public void processLens() {
        if (!enabled || !lensArmed || !lensing.enabled) return;
        if (!CustomRendererRegistry.areShadersReady()) return;
        BlackHolePetRenderer.processLens(
                petX, petY, petZ, lensShadow,
                lensStrength.getValue(), photonRing.getValue(), frameDrag.getValue(),
                capture.getValue(), lensStrength.getValue() * 0.5f, tintColor.getColor());
    }

    /**
     * The point beside the neck the hole is told to sit on: a shoulder in the
     * player's own frame of reference, drifted in a slow idle circle so the pet
     * is never dead still.
     */
    private Vec3 shoulderAnchor(float partialTick) {
        Vec3 base = mc.player.getPosition(partialTick);
        float yaw = (float) Math.toRadians(mc.player.getPreciseBodyRotation(partialTick));
        float forwardX = (float) -Math.sin(yaw);
        float forwardZ = (float) Math.cos(yaw);
        // The player's right is their heading turned a quarter turn towards -X,
        // so the left shoulder sits on the negative side of it.
        float sideSign = shoulder.is("Left") ? -1.0f : 1.0f;
        float rightX = (float) -Math.cos(yaw) * sideSign;
        float rightZ = (float) Math.sin(yaw) * sideSign;

        float drift = orbitRadius.getValue();
        float orbitX = (float) Math.cos(follow.orbitPhase) * drift;
        float orbitZ = (float) Math.sin(follow.orbitPhase) * drift;
        float bobY = (float) Math.sin(follow.orbitPhase * 2.0) * bob.getValue();

        return new Vec3(
                base.x + rightX * (sideOffset.getValue() + orbitX)
                        + forwardX * (forwardOffset.getValue() + orbitZ),
                base.y + height.getValue() + bobY,
                base.z + rightZ * (sideOffset.getValue() + orbitX)
                        + forwardZ * (forwardOffset.getValue() + orbitZ));
    }
}
