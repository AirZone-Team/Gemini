package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.TrailRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.trail.TrailPoint;
import geminiclient.gemini.values.impl.FloatValue;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ribbon Trail — flowing energy ribbon behind the player.
 *
 * <h3>Visual design</h3>
 * <ul>
 *   <li>Ribbon mesh from position history — camera-facing quads</li>
 *   <li>Flowing energy pattern — scrolling noise with veins</li>
 *   <li>Rainbow colour cycler — hue shifts along trail + over time</li>
 *   <li>Exponential glow — tight core + wide bloom aura</li>
 *   <li>HDR output — values > 1.0 picked up by bloom</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <p>Trail points: 40–120 (configurable).  One draw call per frame.
 * Mesh generated entirely on CPU each frame from the position ring buffer.</p>
 */
public class Trail extends Module {

    // ── Config values ──────────────────────────────────────────────

    /** Half-width of the ribbon in world units. */
    private final FloatValue width        = new FloatValue("Width", 0.25f, 0.05f, 1.5f);

    /** Lifetime of each trail point (seconds).  Longer = longer tail. */
    private final FloatValue life         = new FloatValue("Life", 0.6f, 0.1f, 3.0f);

    /** Maximum number of trail points stored. */
    private final FloatValue maxPoints    = new FloatValue("Max Points", 80f, 20f, 200f);

    /** Master transparency. */
    private final FloatValue alpha        = new FloatValue("Alpha", 0.85f, 0.1f, 1.0f);

    /** Brightness / intensity multiplier (0–1; HDR boost is in-shader). */
    private final FloatValue intensity    = new FloatValue("Intensity", 1.0f, 0.1f, 1.0f);

    /** Rainbow hue speed multiplier. */
    private final FloatValue rainbowSpeed = new FloatValue("Rainbow Speed", 1.0f, 0.0f, 3.0f);

    // ── State ──────────────────────────────────────────────────────

    /** Ring buffer of trail points (newest first, oldest last). */
    private final List<TrailPoint> points = new ArrayList<>();

    /** Accumulated animation time (seconds). */
    private float elapsedTime;

    /** Tick counter for throttling point capture. */
    private int tickCounter;

    /** Whether the player was moving last tick. */
    private boolean wasMoving;

    // ── Constructor ────────────────────────────────────────────────

    public Trail() {
        super("Trail", ModuleEnum.Visual);
        addValue(width, life, maxPoints, alpha, intensity, rainbowSpeed);
    }

    @Override
    public void onDisabled() {
        points.clear();
        elapsedTime = 0f;
        tickCounter = 0;
        wasMoving = false;
    }

    // ── Update ──────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        float dt = 0.05f;
        elapsedTime += dt * rainbowSpeed.getValue();
        if (elapsedTime > 3600f) elapsedTime -= 3600f;

        // ── Tick all existing points ─────────────────────────────
        for (TrailPoint pt : points) {
            pt.tick(dt);
        }

        // ── Remove expired points ────────────────────────────────
        points.removeIf(TrailPoint::expired);

        // ── Capture new point ────────────────────────────────────
        boolean moving = mc.player.getDeltaMovement().lengthSqr() > 0.0001;
        tickCounter++;

        // Capture points more frequently when moving, sparsely when idle
        int captureInterval = moving ? 1 : 5;
        if (tickCounter >= captureInterval) {
            tickCounter = 0;

            Vec3 pos = mc.player.position();
            // Offset to player's waist height for natural trail position
            Vec3 trailPos = new Vec3(pos.x, pos.y + 0.9, pos.z);

            points.add(0, new TrailPoint(trailPos, life.getValue()));

            // Enforce max points limit
            int maxPts = (int) maxPoints.getValue();
            while (points.size() > maxPts) {
                points.remove(points.size() - 1);
            }
        }

        wasMoving = moving;
    }

    // ── Render ──────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
//        if (mc.player == null || points.size() < 2) return;

        TrailRenderer.draw(
                event.poseStack(),
                points,
                width.getValue(),
                intensity.getValue(),
                alpha.getValue());
    }
}
