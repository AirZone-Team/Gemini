package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.GhostAfterImageRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.ghost.GhostFrame;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Ghost AfterImage (残影) — cinematic motion trail as silhouette billboards.
 *
 * <h3>Visual design</h3>
 * <p>Spawns translucent, additive-blended SDF silhouette billboards at
 * recent player positions.  Each ghost fades from full opacity to zero
 * over its lifetime, creating a cinematic "speed mirage" effect.</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Every ~0.03s, capture the player's position, rotation, and
 *       pose flags (crouch/sprint/attack)</li>
 *   <li>On render, draw a single camera-facing billboard per ghost frame</li>
 *   <li>The ghost shader constructs a humanoid silhouette via SDF
 *       (head circle + rounded-rect torso/limbs) with blue/cyan tint</li>
 * </ol>
 *
 * <h3>Performance</h3>
 * <p>Up to 20 ghost frames.  One quad per ghost → 20 quads per frame.
 * Additive blending means no draw-order issues.</p>
 */
public class GhostAfterImage extends Module {

    // ── Config values ──────────────────────────────────────────────

    /** Time between ghost captures (seconds).  Lower = denser ghosts. */
    private final FloatValue captureInterval = new FloatValue("Interval", 0.03f, 0.01f, 0.15f);

    /** Lifetime of each ghost frame (seconds). */
    private final FloatValue ghostLife       = new FloatValue("Life", 0.45f, 0.1f, 2.0f);

    /** Maximum number of ghost frames. */
    private final FloatValue maxGhosts       = new FloatValue("Max Ghosts", 16f, 2f, 30f);

    /** Master transparency. */
    private final FloatValue alpha           = new FloatValue("Alpha", 0.7f, 0.1f, 1.0f);

    /** Ghost tint colour (electric blue/cyan by default). */
    private final ColorValue ghostColor      = new ColorValue("Ghost Color", 0xFF4DA6FF);

    // ── State ──────────────────────────────────────────────────────

    private final List<GhostFrame> ghosts = new ArrayList<>();
    private float captureTimer;

    // ── Constructor ────────────────────────────────────────────────

    public GhostAfterImage() {
        super("GhostAfterImage", ModuleEnum.Visual);
        addValue(captureInterval, ghostLife, maxGhosts, alpha, ghostColor);
    }

    @Override
    public void onDisabled() {
        ghosts.clear();
        captureTimer = 0f;
    }

    // ── Update ──────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        float dt = 0.05f;

        // ── Tick existing ghosts ──────────────────────────────────
        for (GhostFrame g : ghosts) {
            g.tick(dt);
        }
        ghosts.removeIf(GhostFrame::expired);

        // ── Capture new ghost frame ───────────────────────────────
        captureTimer += dt;
        if (captureTimer >= captureInterval.getValue()) {
            captureTimer -= captureInterval.getValue();

            var p = mc.player;

            GhostFrame frame = new GhostFrame(
                    p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot(),
                    p.yBodyRot, p.yHeadRot,
                    p.attackAnim,
                    ghostLife.getValue());

            ghosts.add(0, frame);

            // Enforce limit
            int maxG = (int) maxGhosts.getValue();
            while (ghosts.size() > maxG) {
                ghosts.remove(ghosts.size() - 1);
            }
        }
    }

    // ── Render ──────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || ghosts.isEmpty()) return;

        // Decode ghost colour
        int col = ghostColor.getColor();
        float tr = ((col >> 16) & 0xFF) / 255f;
        float tg = ((col >> 8) & 0xFF) / 255f;
        float tb = (col & 0xFF) / 255f;

        GhostAfterImageRenderer.drawGhosts(
                event.poseStack(),
                ghosts,
                tr, tg, tb);
    }

    // ── Public queries ─────────────────────────────────────────────

    public boolean hasActiveGhosts() {
        return !ghosts.isEmpty();
    }
}
