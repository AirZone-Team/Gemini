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
 * Instanced particle system (实例化粒子海): thousands of particles orbiting
 * the player in a cylindrical volume, drifting upward with randomized velocities.
 *
 * Five visual types, selected per particle from the enabled toggles:
 * <ul>
 *     <li>Rune: glowing runic diamonds in gold/purple.</li>
 *     <li>Hexagon: geometric cyan/teal tiles.</li>
 *     <li>Triangle: sharp orange/red shards.</li>
 *     <li>Feather: soft pink/purple ethereal wisps.</li>
 *     <li>Starlight: bright white/silver 4-point stars.</li>
 * </ul>
 *
 * All particles are batched into a single vertex buffer per frame, so the whole
 * system costs one draw call.
 */
public final class InstancedParticle extends Module {

    private static final float TICK_DT = 0.05f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Emission
    private final FloatValue maxParticles = new FloatValue("Max Particles", 3000f, 500f, 6000f);
    private final FloatValue spawnRate = new FloatValue("Spawn Rate", 150f, 20f, 500f);
    private final FloatValue orbitRadius = new FloatValue("Orbit Radius", 2.5f, 0.5f, 6.0f);
    private final FloatValue orbitHeight = new FloatValue("Orbit Height", 2.0f, 0.5f, 5.0f);

    // Appearance
    private final FloatValue particleLife = new FloatValue("Life", 1.5f, 0.3f, 4.0f);
    private final FloatValue particleSize = new FloatValue("Size", 0.15f, 0.03f, 0.6f);
    private final FloatValue intensity = new FloatValue("Intensity", 1.0f, 0.1f, 1.0f);

    // Particle types
    private final BoolValue runes = new BoolValue("Runes", true);
    private final BoolValue hexagons = new BoolValue("Hexagons", true);
    private final BoolValue triangles = new BoolValue("Triangles", true);
    private final BoolValue feathers = new BoolValue("Feathers", true);
    private final BoolValue starlights = new BoolValue("Starlights", true);

    // State
    private final List<ParticleData> particles = new ArrayList<>();
    private final Random rand = new Random();
    private float spawnAccum;

    public InstancedParticle() {
        super("InstancedParticle", ModuleEnum.Visual);
        addValue(maxParticles, spawnRate, orbitRadius, orbitHeight,
                particleLife, particleSize, intensity,
                runes, hexagons, triangles, feathers, starlights);
    }

    @Override
    public void onDisabled() {
        particles.clear();
        spawnAccum = 0f;
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        // Tick existing particles
        for (ParticleData p : particles) {
            p.tick(TICK_DT);
        }
        particles.removeIf(p -> !p.alive);

        // Spawn new particles
        int maxP = (int) maxParticles.getValue();
        spawnAccum += spawnRate.getValue() * TICK_DT;

        while (spawnAccum >= 1f && particles.size() < maxP) {
            spawnAccum -= 1f;
            spawnParticle();
        }
    }

    private void spawnParticle() {
        var player = mc.player;
        if (player == null) return;

        // Cylindrical orbit volume around the player
        float angle = rand.nextFloat() * TWO_PI;
        float r = orbitRadius.getValue() * (0.3f + rand.nextFloat() * 0.7f);
        float h = (rand.nextFloat() - 0.5f) * 2f * orbitHeight.getValue();

        float px = (float) (player.getX() + Math.cos(angle) * r);
        float py = (float) (player.getY() + 1.0 + h);
        float pz = (float) (player.getZ() + Math.sin(angle) * r);

        // Tangential velocity (orbit) + slight upward drift
        float speed = 1.5f + rand.nextFloat() * 3f;
        float vx = (float) (-Math.sin(angle) * speed);
        float vy = 0.3f + rand.nextFloat() * 1.5f;
        float vz = (float) (Math.cos(angle) * speed);

        float life = particleLife.getValue() * (0.6f + rand.nextFloat() * 0.4f);
        float size = particleSize.getValue() * (0.5f + rand.nextFloat() * 0.5f);

        // Random colour variation
        float cr = 0.7f + rand.nextFloat() * 0.3f;
        float cg = 0.7f + rand.nextFloat() * 0.3f;
        float cb = 0.7f + rand.nextFloat() * 0.3f;

        particles.add(new ParticleData(
                px, py, pz, vx, vy, vz,
                life, size, cr, cg, cb, 1f, pickType()));
    }

    private byte pickType() {
        BoolValue[] types = {runes, hexagons, triangles, feathers, starlights};

        int enabled = 0;
        for (BoolValue type : types) {
            if (type.enabled) enabled++;
        }
        if (enabled == 0) return 0; // fallback to runes

        int pick = rand.nextInt(enabled);
        for (byte i = 0; i < types.length; i++) {
            if (types[i].enabled && pick-- == 0) return i;
        }
        return 0;
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || particles.isEmpty()) return;

        InstancedParticleRenderer.draw(
                event.poseStack(),
                particles,
                intensity.getValue());
    }
}
