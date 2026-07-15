package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.MagicHaloRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.world.phys.Vec3;

/**
 * Magic Halo (魔法光环) — 湮灭日食 (Annihilation Eclipse) variant.
 *
 * <p>A dark sci-fi high-contrast halo ornament floating above the
 * player's head. Compresses a chaotic singularity field and
 * razor-sharp crystal shards into the halo silhouette.</p>
 *
 * <h3>Visual design</h3>
 * <ul>
 *   <li>Central circular ring — structural backbone</li>
 *   <li>Floating shards — noise-driven fractured crystal spikes
 *       with variable lengths, replacing uniform triangular rays</li>
 *   <li>Forked crown spikes — compound geometric tines at the top</li>
 *   <li>Void palette — deep purple → crimson → aurora silver</li>
 *   <li>Accretion-disk core glow — dense, throbbing singularity pulse</li>
 *   <li>Orbiting sparkles + harsh broken-crystal edges</li>
 * </ul>
 *
 * <h3>Coordination</h3>
 * <p>The deep purple-crimson energy field with silver highlights
 * creates a dramatic contrast against any background, shifting
 * the visual emphasis from warmth to raw annihilating energy.</p>
 *
 * <h3>Technical</h3>
 * <p>Rendered as a single horizontal billboard quad on the XZ plane
 * via {@link MagicHaloRenderer}.  All geometric detail (ring, shards,
 * crown tines, accretion glow, sparkles) is computed in the fragment
 * shader ({@code magic_halo.fsh}) for GPU efficiency.</p>
 */
public class MagicHalo extends Module {

    // ── Config values ────────────────────────────────────────────────

    /** Halo billboard half-size in world units. */
    private final FloatValue size        = new FloatValue("Size", 1.0f, 0.3f, 3.0f);

    /** Vertical offset above the player's eye height. */
    private final FloatValue heightOffset = new FloatValue("Height", 0.55f, -1.0f, 2.5f);

    /** Master transparency. */
    private final FloatValue alpha       = new FloatValue("Alpha", 0.85f, 0.1f, 1.0f);

    /** Brightness / intensity multiplier. */
    private final FloatValue intensity   = new FloatValue("Intensity", 1.0f, 0.2f, 2.0f);

    /** Number of regular triangular spikes around the ring. */
    private final IntValue   spikeCount  = new IntValue("Spikes", 12, 4, 16);

    /** Animation speed multiplier (0 = frozen). */
    private final FloatValue animSpeed   = new FloatValue("Anim Speed", 1.0f, 0.0f, 3.0f);

    // ── State ────────────────────────────────────────────────────────

    /** Accumulated animation time in seconds. */
    private float elapsedTime;

    // ── Constructor ──────────────────────────────────────────────────

    public MagicHalo() {
        super("MagicHalo", ModuleEnum.Visual);
        addValue(size, heightOffset, alpha, intensity, spikeCount, animSpeed);
    }

    @Override
    public void onDisabled() {
        elapsedTime = 0f;
    }

    // ── Render ───────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        // ── Advance animation time ────────────────────────────────
        // dt ≈ 0.05s at 20 tps; multiplied by animSpeed for rate control
        elapsedTime += 0.05f * animSpeed.getValue();
        // Prevent float precision drift over very long sessions
        if (elapsedTime > 3600f) elapsedTime -= 3600f;

        // ── Compute halo position (interpolated for smooth movement) ─
        // Use getPosition(partialTick) so the halo follows the player
        // at render-frame rate, not tick rate — eliminating jitter/stutter
        // when the player moves, strafes, or is pushed.
        Vec3 pos = mc.player.getPosition(event.partialTick());
        float haloX = (float) pos.x;
        float haloY = (float) (pos.y + mc.player.getEyeHeight() + heightOffset.getValue());
        float haloZ = (float) pos.z;

        // ── Draw the halo ─────────────────────────────────────────
        MagicHaloRenderer.draw(
                event.poseStack(),
                haloX, haloY, haloZ,
                size.getValue(),
                elapsedTime,
                spikeCount.getValue(),
                intensity.getValue(),
                alpha.getValue()
        );
    }
}
