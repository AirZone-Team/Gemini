package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackInstance;
import geminiclient.gemini.customRenderer.glsl.modules.SweepAttackRenderer;
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
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * Highly configurable, multi-layer sweeping attack visual effect.
 *
 * <p>The module exposes complete art direction rather than one hard-coded
 * cyan slash: five procedural materials, four color flows, six presets and
 * independently controllable arc, echo, line, particle, lightning, ring,
 * burst and post-processing layers.</p>
 */
public final class SweepingAttackVFX extends Module {

    private static final int MAX_EFFECTS = 12;

    // Art direction
    private final ListValue preset = new ListValue("Preset", "Celestial",
            new String[]{"Celestial", "Arcane", "Cyber", "Inferno", "Void", "Prismatic", "Custom"});
    private final ListValue style = new ListValue("Slash Style", "Crescent",
            new String[]{"Blade", "Crescent", "Runic", "Plasma", "Shatter"});
    private final ListValue colorMode = new ListValue("Color Flow", "Gradient",
            new String[]{"Static", "Gradient", "Rainbow", "Pulse"});
    private final ListValue quality = new ListValue("Quality", "High",
            new String[]{"Low", "Medium", "High", "Ultra"});
    private final ColorValue primaryColor = new ColorValue("Primary Color", 0xFF64D8FF);
    private final ColorValue accentColor = new ColorValue("Accent Color", 0xFFC27CFF);
    private final ColorValue coreColor = new ColorValue("Core Color", 0xFFFFFFFF);

    // Global geometry and animation
    private final FloatValue intensity = new FloatValue("Intensity", 1.25f, 0.1f, 3f);
    private final FloatValue opacity = new FloatValue("Opacity", 0.92f, 0.05f, 1f);
    private final FloatValue radius = new FloatValue("Radius", 2.35f, 0.5f, 6f);
    private final FloatValue arcAngle = new FloatValue("Arc Angle", 145f, 35f, 320f);
    private final FloatValue heightOffset = new FloatValue("Height", 1.05f, 0.05f, 2.8f);
    private final FloatValue verticalLift = new FloatValue("Arc Lift", 0.42f, -1.5f, 1.5f);
    private final FloatValue thickness = new FloatValue("Thickness", 0.82f, 0.12f, 2.5f);
    private final FloatValue glow = new FloatValue("Glow", 1.55f, 0f, 3f);
    private final FloatValue noise = new FloatValue("Turbulence", 0.35f, 0f, 1f);
    private final FloatValue flowSpeed = new FloatValue("Energy Flow", 1.25f, -3f, 4f);
    private final IntValue duration = new IntValue("Duration", 1450, 250, 4000);
    private final FloatValue animationSpeed = new FloatValue("Animation Speed", 1f, 0.25f, 3f);

    // Arc and echoes
    private final BoolValue enableArc = new BoolValue("Energy Arc", true);
    private final IntValue layers = new IntValue("Arc Layers", 4, 1, 5,
            () -> enableArc.enabled);
    private final IntValue echoes = new IntValue("Afterimages", 3, 0, 5,
            () -> enableArc.enabled);
    private final FloatValue echoSpacing = new FloatValue("Echo Spacing", 0.075f, 0.02f, 0.22f,
            () -> enableArc.enabled && echoes.getValue() > 0);

    // Speed lines
    private final BoolValue enableSpeedLines = new BoolValue("Speed Lines", true);
    private final IntValue speedLineCount = new IntValue("Line Count", 30, 0, 64,
            () -> enableSpeedLines.enabled);
    private final FloatValue lineLength = new FloatValue("Line Length", 0.95f, 0.15f, 2.5f,
            () -> enableSpeedLines.enabled);
    private final FloatValue lineWidth = new FloatValue("Line Width", 0.028f, 0.005f, 0.12f,
            () -> enableSpeedLines.enabled);

    // Particles
    private final BoolValue enableParticles = new BoolValue("Particles", true);
    private final IntValue particleCount = new IntValue("Particle Count", 88, 0, 128,
            () -> enableParticles.enabled);
    private final FloatValue particleSpeed = new FloatValue("Particle Speed", 6.5f, 0.5f, 14f,
            () -> enableParticles.enabled);
    private final FloatValue particleSpread = new FloatValue("Particle Spread", 0.7f, 0f, 2f,
            () -> enableParticles.enabled);
    private final FloatValue particleSize = new FloatValue("Particle Size", 0.18f, 0.025f, 0.6f,
            () -> enableParticles.enabled);
    private final FloatValue particleGravity = new FloatValue("Particle Gravity", 2.6f, -4f, 9f,
            () -> enableParticles.enabled);

    // Lightning
    private final BoolValue enableLightning = new BoolValue("Lightning", true);
    private final IntValue lightningBolts = new IntValue("Bolt Count", 7, 0, 12,
            () -> enableLightning.enabled);
    private final FloatValue lightningWidth = new FloatValue("Bolt Width", 0.032f, 0.006f, 0.12f,
            () -> enableLightning.enabled);

    // Rings and flash
    private final BoolValue enableRing = new BoolValue("Shockwave Rings", true);
    private final IntValue ringCount = new IntValue("Ring Count", 3, 1, 5,
            () -> enableRing.enabled);
    private final FloatValue ringScale = new FloatValue("Ring Scale", 1.45f, 0.3f, 3f,
            () -> enableRing.enabled);
    private final FloatValue ringThickness = new FloatValue("Ring Thickness", 0.34f, 0.02f, 1f,
            () -> enableRing.enabled);
    private final BoolValue enableCoreBurst = new BoolValue("Core Burst", true);

    // Screen-space finish
    private final BoolValue enablePost = new BoolValue("Post FX", true);
    private final FloatValue distortion = new FloatValue("Screen Distortion", 0.38f, 0f, 1.5f,
            () -> enablePost.enabled);
    private final FloatValue chromatic = new FloatValue("Chromatic Split", 0.28f, 0f, 1.5f,
            () -> enablePost.enabled);
    private final FloatValue flash = new FloatValue("Screen Flash", 0.32f, 0f, 1f,
            () -> enablePost.enabled);
    private final FloatValue vignette = new FloatValue("Impact Vignette", 0.18f, 0f, 1f,
            () -> enablePost.enabled);

    // Performance
    private final IntValue maxEffects = new IntValue("Max Effects", 5, 1, MAX_EFFECTS);
    private final FloatValue renderDistance = new FloatValue("Render Distance", 32f, 6f, 96f);

    private final SweepAttackInstance[] effects = new SweepAttackInstance[MAX_EFFECTS];
    private int effectCount;
    private long lastUpdateNanos;
    private boolean applyingPreset;

    public SweepingAttackVFX() {
        super("SweepingAttackVFX", ModuleEnum.Visual);
        addValue(
                preset, style, colorMode, quality,
                primaryColor, accentColor, coreColor,
                intensity, opacity, radius, arcAngle, heightOffset, verticalLift,
                thickness, glow, noise, flowSpeed, duration, animationSpeed,
                enableArc, layers, echoes, echoSpacing,
                enableSpeedLines, speedLineCount, lineLength, lineWidth,
                enableParticles, particleCount, particleSpeed, particleSpread,
                particleSize, particleGravity,
                enableLightning, lightningBolts, lightningWidth,
                enableRing, ringCount, ringScale, ringThickness, enableCoreBurst,
                enablePost, distortion, chromatic, flash, vignette,
                maxEffects, renderDistance
        );
        preset.setOnChange(this::applyPreset);
    }

    @Override
    public void onEnabled() {
        lastUpdateNanos = System.nanoTime();
    }

    @Override
    public void onDisabled() {
        clearAllEffects();
    }

    /**
     * Spawn toward the attacked entity. Called by {@code MixinPlayer}.
     */
    public void spawnSweepEffect(net.minecraft.world.entity.player.Player player,
                                 double targetX, double targetZ) {
        if (mc.player == null || mc.level == null) return;
        double deltaX = targetX - player.getX();
        double deltaZ = targetZ - player.getZ();
        double length = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float directionX;
        float directionZ;
        if (length > 0.001) {
            directionX = (float) (deltaX / length);
            directionZ = (float) (deltaZ / length);
        } else {
            float yaw = (float) Math.toRadians(player.getYRot());
            directionX = (float) -Math.sin(yaw);
            directionZ = (float) Math.cos(yaw);
        }

        float baseAngle = (float) Math.atan2(directionZ, directionX);
        float range = (float) Math.toRadians(arcAngle.getValue());
        spawnEffect(player.position(), directionX, directionZ,
                baseAngle - range * 0.5f, baseAngle + range * 0.5f);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;
        long nowNanos = System.nanoTime();
        float deltaTime = lastUpdateNanos == 0L
                ? 0.05f
                : Math.min(0.1f, Math.max(0.001f, (nowNanos - lastUpdateNanos) / 1_000_000_000f));
        lastUpdateNanos = nowNanos;
        long nowMs = System.currentTimeMillis();

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance instance = effects[i];
            if (instance == null || !instance.alive) continue;
            if (enableParticles.enabled) instance.updateParticles(deltaTime);
            if (!instance.isAlive(nowMs)) instance.alive = false;
        }
        compactEffects();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (effectCount == 0 || mc.level == null) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;
        long nowMs = System.currentTimeMillis();
        float maxDistance = renderDistance.getValue();
        float maxDistanceSquared = maxDistance * maxDistance;
        SweepAttackRenderer.Config config = createRenderConfig();

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance instance = effects[i];
            if (instance == null || !instance.alive) continue;
            double dx = instance.x - camera.position().x;
            double dy = instance.y - camera.position().y;
            double dz = instance.z - camera.position().z;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) continue;
            SweepAttackRenderer.draw(event.poseStack(), instance, nowMs, config);
        }
    }

    public void processPost() {
        if (!enablePost.enabled || effectCount == 0) return;
        long nowMs = System.currentTimeMillis();
        float maximumDistortion = 0f;
        float maximumChromatic = 0f;
        float maximumFlash = 0f;
        float maximumVignette = 0f;

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance instance = effects[i];
            if (instance == null || !instance.alive) continue;
            float arcFade = instance.arcAlpha(nowMs) * intensity.getValue();
            maximumDistortion = Math.max(maximumDistortion, distortion.getValue() * arcFade);
            maximumChromatic = Math.max(maximumChromatic, chromatic.getValue() * arcFade);
            maximumFlash = Math.max(maximumFlash,
                    flash.getValue() * instance.burstAlpha(nowMs) * intensity.getValue());
            maximumVignette = Math.max(maximumVignette,
                    vignette.getValue() * instance.ringAlpha(nowMs));
        }

        float[] tint = rgb(primaryColor.getColor());
        SweepAttackRenderer.processPost(
                maximumDistortion, maximumChromatic, maximumFlash, maximumVignette,
                tint[0], tint[1], tint[2]);
    }

    private SweepAttackRenderer.Config createRenderConfig() {
        float[] primary = rgb(primaryColor.getColor());
        float[] accent = rgb(accentColor.getColor());
        float[] core = rgb(coreColor.getColor());
        return new SweepAttackRenderer.Config(
                style.index, colorMode.index, quality.index,
                layers.getValue(), echoes.getValue(), ringCount.getValue(),
                speedLineCount.getValue(),
                enableArc.enabled, enableSpeedLines.enabled, enableParticles.enabled,
                enableLightning.enabled, enableRing.enabled, enableCoreBurst.enabled,
                intensity.getValue(), opacity.getValue(), radius.getValue(),
                thickness.getValue(), verticalLift.getValue(), glow.getValue(),
                noise.getValue(), flowSpeed.getValue(), echoSpacing.getValue(),
                ringScale.getValue(), ringThickness.getValue(),
                lineLength.getValue(), lineWidth.getValue(), lightningWidth.getValue(),
                primary[0], primary[1], primary[2],
                accent[0], accent[1], accent[2],
                core[0], core[1], core[2]
        );
    }

    private void spawnEffect(Vec3 position, float directionX, float directionZ,
                             float arcStart, float arcEnd) {
        int limit = maxEffects.getValue();
        while (effectCount >= limit) removeOldestEffect();
        if (effectCount >= MAX_EFFECTS) return;
        effects[effectCount++] = new SweepAttackInstance(
                position, directionX, directionZ, arcStart, arcEnd,
                heightOffset.getValue(), duration.getValue(), animationSpeed.getValue(),
                particleCount.getValue(), particleSpeed.getValue(),
                particleSpread.getValue(), particleSize.getValue(),
                particleGravity.getValue(), lightningBolts.getValue()
        );
    }

    private void removeOldestEffect() {
        if (effectCount == 0) return;
        for (int i = 1; i < effectCount; i++) effects[i - 1] = effects[i];
        effects[--effectCount] = null;
    }

    private void compactEffects() {
        int write = 0;
        for (int read = 0; read < effectCount; read++) {
            SweepAttackInstance instance = effects[read];
            if (instance == null || !instance.alive) continue;
            effects[write++] = instance;
        }
        for (int i = write; i < effectCount; i++) effects[i] = null;
        effectCount = write;
    }

    private void clearAllEffects() {
        for (int i = 0; i < effectCount; i++) effects[i] = null;
        effectCount = 0;
    }

    public boolean hasActiveEffects() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) return true;
        }
        return false;
    }

    private void applyPreset() {
        if (applyingPreset || preset.is("Custom")) return;
        applyingPreset = true;
        try {
            enableArc.setEnabled(true);
            enableSpeedLines.setEnabled(true);
            enableParticles.setEnabled(true);
            enableRing.setEnabled(true);
            enableCoreBurst.setEnabled(true);
            enablePost.setEnabled(true);
            if (preset.is("Arcane")) {
                style.setMode("Runic");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFF9E72FF);
                accentColor.setColor(0xFF55E7FF);
                coreColor.setColor(0xFFFFF2C2);
                intensity.setValue(1.2f);
                radius.setValue(2.2f);
                arcAngle.setValue(155f);
                thickness.setValue(0.72f);
                glow.setValue(1.45f);
                noise.setValue(0.18f);
                layers.setValue(4);
                echoes.setValue(2);
                particleCount.setValue(72);
                lightningBolts.setValue(5);
                ringCount.setValue(3);
                distortion.setValue(0.25f);
                chromatic.setValue(0.2f);
            } else if (preset.is("Cyber")) {
                style.setMode("Blade");
                colorMode.setMode("Pulse");
                primaryColor.setColor(0xFF18F4FF);
                accentColor.setColor(0xFFFF3ED1);
                coreColor.setColor(0xFFFFFFFF);
                intensity.setValue(1.4f);
                radius.setValue(2.5f);
                arcAngle.setValue(125f);
                thickness.setValue(0.4f);
                glow.setValue(1.75f);
                noise.setValue(0.08f);
                flowSpeed.setValue(2.2f);
                layers.setValue(3);
                echoes.setValue(4);
                speedLineCount.setValue(44);
                particleCount.setValue(58);
                enableLightning.setEnabled(false);
                ringCount.setValue(2);
                chromatic.setValue(0.65f);
                distortion.setValue(0.18f);
            } else if (preset.is("Inferno")) {
                style.setMode("Plasma");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFFFF3B16);
                accentColor.setColor(0xFFFFB51B);
                coreColor.setColor(0xFFFFFFC2);
                intensity.setValue(1.65f);
                radius.setValue(2.7f);
                arcAngle.setValue(175f);
                thickness.setValue(1.18f);
                glow.setValue(2.25f);
                noise.setValue(0.82f);
                verticalLift.setValue(0.58f);
                layers.setValue(4);
                echoes.setValue(2);
                particleCount.setValue(112);
                particleGravity.setValue(1.1f);
                lightningBolts.setValue(4);
                ringCount.setValue(4);
                distortion.setValue(0.85f);
                flash.setValue(0.6f);
                vignette.setValue(0.35f);
            } else if (preset.is("Void")) {
                style.setMode("Shatter");
                colorMode.setMode("Pulse");
                primaryColor.setColor(0xFF4B1A9B);
                accentColor.setColor(0xFFD52BBD);
                coreColor.setColor(0xFFEEE2FF);
                intensity.setValue(1.3f);
                radius.setValue(2.85f);
                arcAngle.setValue(210f);
                thickness.setValue(0.94f);
                glow.setValue(1.1f);
                noise.setValue(0.95f);
                flowSpeed.setValue(-1.25f);
                layers.setValue(5);
                echoes.setValue(4);
                particleCount.setValue(92);
                lightningBolts.setValue(10);
                ringCount.setValue(3);
                distortion.setValue(1.05f);
                chromatic.setValue(0.42f);
                vignette.setValue(0.62f);
            } else if (preset.is("Prismatic")) {
                style.setMode("Crescent");
                colorMode.setMode("Rainbow");
                primaryColor.setColor(0xFF52E5FF);
                accentColor.setColor(0xFFFF66D8);
                coreColor.setColor(0xFFFFFFFF);
                intensity.setValue(1.5f);
                radius.setValue(2.55f);
                arcAngle.setValue(190f);
                thickness.setValue(0.78f);
                glow.setValue(2.05f);
                noise.setValue(0.25f);
                flowSpeed.setValue(1.6f);
                layers.setValue(5);
                echoes.setValue(3);
                speedLineCount.setValue(38);
                particleCount.setValue(104);
                lightningBolts.setValue(8);
                ringCount.setValue(5);
                chromatic.setValue(0.72f);
                flash.setValue(0.48f);
            } else {
                // Celestial
                style.setMode("Crescent");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFF64D8FF);
                accentColor.setColor(0xFFC27CFF);
                coreColor.setColor(0xFFFFFFFF);
                intensity.setValue(1.25f);
                opacity.setValue(0.92f);
                radius.setValue(2.35f);
                arcAngle.setValue(145f);
                verticalLift.setValue(0.42f);
                thickness.setValue(0.82f);
                glow.setValue(1.55f);
                noise.setValue(0.35f);
                flowSpeed.setValue(1.25f);
                layers.setValue(4);
                echoes.setValue(3);
                speedLineCount.setValue(30);
                particleCount.setValue(88);
                particleGravity.setValue(2.6f);
                enableLightning.setEnabled(true);
                lightningBolts.setValue(7);
                ringCount.setValue(3);
                ringScale.setValue(1.45f);
                distortion.setValue(0.38f);
                chromatic.setValue(0.28f);
                flash.setValue(0.32f);
                vignette.setValue(0.18f);
            }
        } finally {
            applyingPreset = false;
        }
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
        };
    }
}
