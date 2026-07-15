package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.InstancedParticleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.ParticleData;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.FloatValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Instanced Particle System (实例化粒子海).
 *
 * <h3>5000+ particles with 5 visual types</h3>
 * <ul>
 *   <li>RUNE      — glowing runic diamonds in gold/purple</li>
 *   <li>HEXAGON   — geometric cyan/teal tiles</li>
 *   <li>TRIANGLE  — sharp orange/red shards</li>
 *   <li>FEATHER   — soft pink/purple ethereal wisps</li>
 *   <li>STARLIGHT — bright white/silver 4-point stars</li>
 * </ul>
 *
 * <h3>Emission pattern</h3>
 * <p>Particles orbit the player in a cylindrical volume,
 * slowly drifting upward with randomized velocities.</p>
 *
 * <h3>Performance</h3>
 * <p>All particles batched into a single vertex buffer per frame
 * → one draw call.  Target: 5000 particles @ 60fps.</p>
 */
public class InstancedParticle extends Module {

    // ── Config ────────────────────────────────────────────────────

    private final FloatValue maxParticles = new FloatValue("Max Particles", 3000f, 500f, 6000f);
    private final FloatValue spawnRate    = new FloatValue("Spawn Rate", 150f, 20f, 500f);
    private final FloatValue particleLife = new FloatValue("Life", 1.5f, 0.3f, 4.0f);
    private final FloatValue particleSize = new FloatValue("Size", 0.15f, 0.03f, 0.6f);
    private final FloatValue orbitRadius  = new FloatValue("Orbit Radius", 2.5f, 0.5f, 6.0f);
    private final FloatValue orbitHeight  = new FloatValue("Orbit Height", 2.0f, 0.5f, 5.0f);
    private final FloatValue intensity    = new FloatValue("Intensity", 1.0f, 0.1f, 1.0f);
    private final BoolValue  runes       = new BoolValue("Runes", true);
    private final BoolValue  hexagons    = new BoolValue("Hexagons", true);
    private final BoolValue  triangles   = new BoolValue("Triangles", true);
    private final BoolValue  feathers    = new BoolValue("Feathers", true);
    private final BoolValue  starlights  = new BoolValue("Starlights", true);

    // ── State ─────────────────────────────────────────────────────

    private final List<ParticleData> particles = new ArrayList<>();
    private final Random rand = new Random();
    private float spawnAccum;

    // ── Constructor ────────────────────────────────────────────────

    public InstancedParticle() {
        super("InstancedParticle", ModuleEnum.Visual);
        addValue(maxParticles, spawnRate, particleLife, particleSize,
                orbitRadius, orbitHeight, intensity,
                runes, hexagons, triangles, feathers, starlights);
    }

    @Override
    public void onDisabled() {
        particles.clear();
        spawnAccum = 0f;
    }

    // ── Update ──────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        float dt = 0.05f;

        // ── Tick existing particles ──────────────────────────────
        for (ParticleData p : particles) {
            p.tick(dt);
        }
        particles.removeIf(p -> !p.alive);

        // ── Spawn new particles ──────────────────────────────────
        int maxP = (int) maxParticles.getValue();
        spawnAccum += spawnRate.getValue() * dt;

        while (spawnAccum >= 1f && particles.size() < maxP) {
            spawnAccum -= 1f;
            spawnParticle();
        }
    }

    private void spawnParticle() {
        var p = mc.player;
        if (p == null) return;

        // Cylindrical orbit volume around player
        float angle = rand.nextFloat() * 6.2832f;
        float r = orbitRadius.getValue() * (0.3f + rand.nextFloat() * 0.7f);
        float h = (rand.nextFloat() - 0.5f) * 2f * orbitHeight.getValue();

        float px = (float)(p.getX() + Math.cos(angle) * r);
        float py = (float)(p.getY() + 1.0 + h);
        float pz = (float)(p.getZ() + Math.sin(angle) * r);

        // Tangential velocity (orbit) + slight upward drift
        float speed = 1.5f + rand.nextFloat() * 3f;
        float vx = (float)(-Math.sin(angle) * speed);
        float vy = 0.3f + rand.nextFloat() * 1.5f;
        float vz = (float)(Math.cos(angle) * speed);

        float life = particleLife.getValue() * (0.6f + rand.nextFloat() * 0.4f);
        float size = particleSize.getValue() * (0.5f + rand.nextFloat() * 0.5f);

        // Random colour variation
        float cr = 0.7f + rand.nextFloat() * 0.3f;
        float cg = 0.7f + rand.nextFloat() * 0.3f;
        float cb = 0.7f + rand.nextFloat() * 0.3f;

        // Pick type from enabled types
        byte type = pickType();

        particles.add(new ParticleData(
                px, py, pz, vx, vy, vz,
                life, size, cr, cg, cb, 1f, type));
    }

    private byte pickType() {
        // Build weighted list of enabled types
        int count = 0;
        if (runes.enabled) count++;
        if (hexagons.enabled) count++;
        if (triangles.enabled) count++;
        if (feathers.enabled) count++;
        if (starlights.enabled) count++;

        if (count == 0) return 0; // fallback to runes

        int pick = rand.nextInt(count);
        int idx = 0;
        if (runes.enabled && idx++ == pick) return 0;
        if (hexagons.enabled && idx++ == pick) return 1;
        if (triangles.enabled && idx++ == pick) return 2;
        if (feathers.enabled && idx++ == pick) return 3;
        return 4; // starlights
    }

    // ── Render ──────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || particles.isEmpty()) return;

        InstancedParticleRenderer.draw(
                event.poseStack(),
                particles,
                intensity.getValue());
    }
}
