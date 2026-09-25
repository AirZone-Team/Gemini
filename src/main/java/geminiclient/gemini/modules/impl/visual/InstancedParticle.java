package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.InstancedParticleRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.ParticleData;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Instanced particle sea (实例粒子): thousands of glowing sigils orbiting the
 * player, batched into one vertex buffer and drawn in a single pass.
 *
 * <p>The five sigils are drawn as neon line-work in the same language as
 * {@link JumpCircle} rather than as flat filled tiles:</p>
 * <ul>
 *     <li>Rune: a diamond cell with a glyph picked per particle.</li>
 *     <li>Hexagon: a dashed frame with a counter-running hairline.</li>
 *     <li>Triangle: a shard outline with an inner echo and a hot apex.</li>
 *     <li>Feather: a lens blade with a rib and swept barbs.</li>
 *     <li>Starlight: a four-point star with rays that taper to nothing.</li>
 * </ul>
 */
public final class InstancedParticle extends Module {

    private static final float TICK_DT = 0.05f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Presets
    private final ListValue preset = new ListValue("Preset", "Arcane",
            new String[]{"Arcane", "Cyber", "Celestial", "Inferno", "Void", "Custom"});

    // Emission
    private final FloatValue maxParticles = new FloatValue("Max Particles", 3000f, 500f, 6000f);
    private final FloatValue spawnRate = new FloatValue("Spawn Rate", 150f, 20f, 500f);
    private final FloatValue orbitRadius = new FloatValue("Orbit Radius", 2.5f, 0.5f, 6.0f);
    private final FloatValue orbitHeight = new FloatValue("Orbit Height", 2.0f, 0.5f, 5.0f);
    private final FloatValue particleLife = new FloatValue("Life", 1.5f, 0.3f, 4.0f);
    private final FloatValue particleSize = new FloatValue("Size", 0.30f, 0.05f, 1.2f);
    private final FloatValue sizeVariation = new FloatValue("Size Variation", 0.6f, 0f, 1f);
    private final FloatValue spin = new FloatValue("Spin", 1.2f, 0f, 6f);

    // Appearance
    private final ListValue style = new ListValue("Style", "Arcane",
            new String[]{"Solid", "Arcane", "Cyber", "Celestial", "Void"});
    private final ListValue colorFlow = new ListValue("Color Flow", "Gradient",
            new String[]{"Static", "Gradient", "Rainbow", "Pulse"});
    private final FloatValue thickness = new FloatValue("Thickness", 0.85f, 0.15f, 2.5f);
    private final FloatValue glow = new FloatValue("Glow", 1.35f, 0f, 2.5f);
    private final FloatValue clarity = new FloatValue("Clarity", 1.45f, 0.4f, 2.2f);
    private final FloatValue brightness = new FloatValue("Brightness", 1.55f, 0.25f, 2.5f);
    private final FloatValue opacity = new FloatValue("Opacity", 0.85f, 0.05f, 1.0f);
    private final FloatValue dynamics = new FloatValue("Dynamics", 1.1f, 0f, 2.0f);
    private final FloatValue accent = new FloatValue("Accent Mix", 0.18f, 0f, 1f);
    private final IntValue detail = new IntValue("Detail", 2, 0, 3);
    private final IntValue echoes = new IntValue("Echoes", 2, 1, 4);
    private final BoolValue orbitRing = new BoolValue("Orbit Ring", true);
    private final BoolValue burst = new BoolValue("Birth Burst", true);
    private final ListValue quality = new ListValue("Quality", "High",
            new String[]{"Low", "Medium", "High", "Ultra"});

    // Palette
    private final ColorValue runeColor = new ColorValue("Rune Color", 0xFFFFD77D);
    private final ColorValue hexagonColor = new ColorValue("Hexagon Color", 0xFF7DEBFF);
    private final ColorValue triangleColor = new ColorValue("Triangle Color", 0xFFFF7A24);
    private final ColorValue featherColor = new ColorValue("Feather Color", 0xFFB69CFF);
    private final ColorValue starlightColor = new ColorValue("Starlight Color", 0xFFEAF4FF);

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
    private boolean applyingPreset;

    public InstancedParticle() {
        super("InstancedParticle", ModuleEnum.Visual);
        addValue(preset,
                maxParticles, spawnRate, orbitRadius, orbitHeight,
                particleLife, particleSize, sizeVariation, spin,
                style, colorFlow, thickness, glow, clarity, brightness, opacity,
                dynamics, accent, detail, echoes, orbitRing, burst, quality,
                runeColor, hexagonColor, triangleColor, featherColor, starlightColor,
                runes, hexagons, triangles, feathers, starlights);
        preset.setOnChange(this::applyPreset);
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

        for (ParticleData p : particles) {
            p.tick(TICK_DT);
        }
        particles.removeIf(p -> !p.alive);

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
        float variation = 1f - sizeVariation.getValue() * rand.nextFloat();
        float size = particleSize.getValue() * variation;

        byte type = pickType();
        int argb = paletteFor(type);
        // Keep the hue but vary the value, so the sea shimmers without the
        // palette drifting towards grey.
        float value = 0.78f + rand.nextFloat() * 0.22f;
        float cr = ((argb >> 16) & 0xFF) / 255f * value;
        float cg = ((argb >> 8) & 0xFF) / 255f * value;
        float cb = (argb & 0xFF) / 255f * value;

        particles.add(new ParticleData(
                px, py, pz, vx, vy, vz,
                life, size, cr, cg, cb,
                rand.nextFloat() * TWO_PI,
                (rand.nextFloat() - 0.5f) * 2f * spin.getValue(),
                rand.nextFloat(),
                type,
                (byte) rand.nextInt(8),
                (byte) (1 + rand.nextInt(echoes.getValue())),
                burst.enabled && rand.nextFloat() < 0.35f));
    }

    private int paletteFor(byte type) {
        return switch (type) {
            case 1 -> hexagonColor.getColor();
            case 2 -> triangleColor.getColor();
            case 3 -> featherColor.getColor();
            case 4 -> starlightColor.getColor();
            default -> runeColor.getColor();
        };
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
                new InstancedParticleRenderer.Settings(
                        colorFlow.index,
                        style.index,
                        detail.getValue(),
                        quality.index,
                        thickness.getValue(),
                        glow.getValue(),
                        clarity.getValue(),
                        brightness.getValue(),
                        opacity.getValue(),
                        dynamics.getValue(),
                        accent.getValue(),
                        orbitRing.enabled));
    }

    private void applyPreset() {
        if (applyingPreset || preset.is("Custom")) return;
        applyingPreset = true;
        try {
            switch (preset.get()) {
                case "Cyber" -> {
                    style.setMode("Cyber");
                    colorFlow.setMode("Pulse");
                    runeColor.setColor(0xFF5CF8FF);
                    hexagonColor.setColor(0xFF40B9FF);
                    triangleColor.setColor(0xFFFF3B92);
                    featherColor.setColor(0xFF9BE8FF);
                    starlightColor.setColor(0xFFFFFFFF);
                    thickness.setValue(0.45f);
                    clarity.setValue(1.9f);
                    dynamics.setValue(1.5f);
                    glow.setValue(1.4f);
                    spin.setValue(2.6f);
                    echoes.setValue(1);
                    detail.setValue(1);
                }
                case "Celestial" -> {
                    style.setMode("Celestial");
                    colorFlow.setMode("Gradient");
                    runeColor.setColor(0xFFFFF1B8);
                    hexagonColor.setColor(0xFF8DBBFF);
                    triangleColor.setColor(0xFF7EE7FF);
                    featherColor.setColor(0xFFD994FF);
                    starlightColor.setColor(0xFFEAF4FF);
                    thickness.setValue(0.6f);
                    clarity.setValue(1.6f);
                    dynamics.setValue(0.8f);
                    glow.setValue(1.75f);
                    spin.setValue(0.9f);
                    echoes.setValue(3);
                    detail.setValue(3);
                }
                case "Inferno" -> {
                    style.setMode("Solid");
                    colorFlow.setMode("Pulse");
                    runeColor.setColor(0xFFFFF0A4);
                    hexagonColor.setColor(0xFFFFB12B);
                    triangleColor.setColor(0xFFFF7A24);
                    featherColor.setColor(0xFFFF301B);
                    starlightColor.setColor(0xFFFFE0B0);
                    thickness.setValue(1.25f);
                    clarity.setValue(1.0f);
                    dynamics.setValue(1.7f);
                    glow.setValue(1.9f);
                    spin.setValue(1.8f);
                    echoes.setValue(2);
                    detail.setValue(1);
                }
                case "Void" -> {
                    style.setMode("Void");
                    colorFlow.setMode("Gradient");
                    runeColor.setColor(0xFFDDC7FF);
                    hexagonColor.setColor(0xFF6C42E8);
                    triangleColor.setColor(0xFF7D65FF);
                    featherColor.setColor(0xFFD82778);
                    starlightColor.setColor(0xFFB69CFF);
                    thickness.setValue(0.8f);
                    clarity.setValue(1.3f);
                    dynamics.setValue(1.25f);
                    glow.setValue(0.9f);
                    spin.setValue(3.4f);
                    echoes.setValue(4);
                    detail.setValue(2);
                }
                default -> {
                    style.setMode("Arcane");
                    colorFlow.setMode("Gradient");
                    runeColor.setColor(0xFFFFD77D);
                    hexagonColor.setColor(0xFF7DEBFF);
                    triangleColor.setColor(0xFFFF7A24);
                    featherColor.setColor(0xFFB69CFF);
                    starlightColor.setColor(0xFFEAF4FF);
                    thickness.setValue(0.85f);
                    clarity.setValue(1.45f);
                    dynamics.setValue(1.1f);
                    glow.setValue(1.35f);
                    spin.setValue(1.2f);
                    echoes.setValue(2);
                    detail.setValue(2);
                }
            }
        } finally {
            applyingPreset = false;
        }
    }
}
