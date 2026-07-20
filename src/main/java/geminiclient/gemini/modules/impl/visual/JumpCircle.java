package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Highly configurable jump and landing impact decals.
 *
 * <p>The effect is rendered procedurally in a single fragment shader. The
 * compact per-draw configuration is encoded into texture coordinates, keeping
 * presets and live customization cheap enough for many simultaneous circles.</p>
 */
public class JumpCircle extends Module {

    // Presets and triggers
    private final ListValue preset = new ListValue("Preset", "Arcane",
            new String[]{"Arcane", "Cyber", "Celestial", "Inferno", "Void", "Custom"});
    private final ListValue style = new ListValue("Style", "Arcane",
            new String[]{"Arcane", "Cyber", "Celestial", "Inferno", "Void"});
    private final ListValue colorFlow = new ListValue("Color Flow", "Gradient",
            new String[]{"Static", "Gradient", "Rainbow", "Pulse"});
    private final ListValue easing = new ListValue("Easing", "Ease Out",
            new String[]{"Linear", "Ease Out", "Elastic", "Pulse"});

    private final BoolValue triggerTakeoff = new BoolValue("Takeoff", true);
    private final BoolValue triggerLanding = new BoolValue("Landing", true);
    private final BoolValue triggerHeavy = new BoolValue("Heavy Landing", true);
    private final CheckboxValue triggers = new CheckboxValue("Triggers",
            new BoolValue[]{triggerTakeoff, triggerLanding, triggerHeavy});

    // Palette
    private final ColorValue takeoffColor = new ColorValue("Takeoff Color", 0xFFB69CFF);
    private final ColorValue normalColor = new ColorValue("Landing Color", 0xFF7DEBFF);
    private final ColorValue heavyColor = new ColorValue("Heavy Color", 0xFFFF5A36);
    private final ColorValue accentColor = new ColorValue("Accent Color", 0xFFFFD77D);
    private final ColorValue shadowColor = new ColorValue("Shadow Color", 0xFF160C2A);
    private final BoolValue dualTone = new BoolValue("Dual Tone", true);

    // Geometry and timing
    private final FloatValue maxRadius = new FloatValue("Radius", 2.25f, 0.4f, 8.0f);
    private final IntValue duration = new IntValue("Duration", 1050, 200, 4000);
    private final FloatValue timeScale = new FloatValue("Time Scale", 1.0f, 0.25f, 3.0f);
    private final FloatValue heightOffset = new FloatValue("Height Offset", 0.018f, 0.002f, 0.12f);
    private final FloatValue heavyThreshold = new FloatValue("Heavy Threshold", 3.0f, 0.5f, 12.0f);
    private final BoolValue impactScaling = new BoolValue("Impact Scaling", true);
    private final FloatValue heavyScale = new FloatValue("Heavy Scale", 1.35f, 1.0f, 2.5f,
            () -> impactScaling.enabled);

    // Material and ornament controls
    private final FloatValue thickness = new FloatValue("Thickness", 0.82f, 0.15f, 2.5f);
    private final FloatValue opacity = new FloatValue("Opacity", 0.92f, 0.05f, 1.0f);
    private final FloatValue brightness = new FloatValue("Brightness", 1.25f, 0.25f, 2.5f);
    private final FloatValue glow = new FloatValue("Glow", 1.15f, 0.0f, 2.5f);
    private final FloatValue distortion = new FloatValue("Distortion", 0.35f, 0.0f, 1.5f);
    private final FloatValue spin = new FloatValue("Spin", 1.0f, -3.0f, 3.0f);
    private final IntValue layers = new IntValue("Layers", 3, 1, 4);
    private final IntValue ringCount = new IntValue("Ring Count", 2, 1, 4);
    private final IntValue runeDetail = new IntValue("Rune Detail", 2, 0, 3);
    private final IntValue particleDensity = new IntValue("Particles", 2, 0, 3);
    private final BoolValue spikes = new BoolValue("Spikes", true);
    private final BoolValue shockwave = new BoolValue("Shockwave", true);
    private final ListValue quality = new ListValue("Quality", "High",
            new String[]{"Low", "Medium", "High", "Ultra"});

    // Grounding and performance
    private final FloatValue shadowOpacity = new FloatValue("Shadow", 0.34f, 0.0f, 1.0f);
    private final FloatValue shadowSize = new FloatValue("Shadow Size", 1.15f, 0.6f, 2.0f,
            () -> shadowOpacity.getValue() > 0.001f);
    private final IntValue maxCircles = new IntValue("Max Circles", 36, 1, 120);
    private final FloatValue cullDistance = new FloatValue("Render Distance", 24.0f, 4.0f, 96.0f);

    private final List<JumpInstance> activeCircles = new ArrayList<>();
    private boolean wasOnGround = true;
    private float peakFallDistance;
    private boolean applyingPreset;

    private enum JumpType {
        TAKEOFF(0),
        LANDING_NORMAL(1),
        LANDING_HEAVY(2);

        final int shaderId;

        JumpType(int shaderId) {
            this.shaderId = shaderId;
        }
    }

    private record JumpInstance(double x, double y, double z, long timestamp, JumpType type, float impact) {}

    public JumpCircle() {
        super("JumpCircle", ModuleEnum.Visual);
        addValue(
                preset, triggers,
                style, colorFlow, easing,
                takeoffColor, normalColor, heavyColor, accentColor, dualTone,
                maxRadius, duration, timeScale, heightOffset,
                heavyThreshold, impactScaling, heavyScale,
                thickness, opacity, brightness, glow, distortion, spin,
                layers, ringCount, runeDetail, particleDensity, spikes, shockwave, quality,
                shadowColor, shadowOpacity, shadowSize,
                maxCircles, cullDistance
        );
        preset.setOnChange(this::applyPreset);
    }

    @Override
    public void onEnabled() {
        wasOnGround = mc.player == null || mc.player.onGround();
        peakFallDistance = 0f;
    }

    @Override
    public void onDisabled() {
        activeCircles.clear();
        peakFallDistance = 0f;
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null || event.getTimeEnum() != TimeEnum.Pre) return;

        boolean onGround = mc.player.onGround();
        if (!onGround) {
            peakFallDistance = (float) Math.max(peakFallDistance, mc.player.fallDistance);
        }

        if (wasOnGround && !onGround && triggerTakeoff.enabled && mc.player.getDeltaMovement().y > 0.05) {
            spawn(mc.player.position(), JumpType.TAKEOFF, 1f);
        }

        if (!wasOnGround && onGround && peakFallDistance > 0.1f) {
            boolean heavy = peakFallDistance >= heavyThreshold.getValue();
            if ((heavy && triggerHeavy.enabled) || (!heavy && triggerLanding.enabled)) {
                float impact = impactScaling.enabled
                        ? clamp(0.8f + peakFallDistance / Math.max(heavyThreshold.getValue(), 0.5f) * 0.25f,
                                0.8f, 1.25f)
                        : 1f;
                spawn(mc.player.position(), heavy ? JumpType.LANDING_HEAVY : JumpType.LANDING_NORMAL, impact);
            }
        }

        if (onGround) peakFallDistance = 0f;
        wasOnGround = onGround;
    }

    private void spawn(Vec3 position, JumpType type, float impact) {
        double groundY = JumpCircleRenderer.findGroundY(position.x, position.y, position.z);
        activeCircles.add(new JumpInstance(position.x, groundY, position.z,
                System.currentTimeMillis(), type, impact));

        int cap = maxCircles.getValue();
        while (activeCircles.size() > cap) activeCircles.removeFirst();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || activeCircles.isEmpty()) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        long now = System.currentTimeMillis();
        int lifeMs = duration.getValue();
        float distance = cullDistance.getValue();
        float cullDistanceSq = distance * distance;
        int styleBits = packStyleBits(false);
        int accentBits = packStyleBits(true);
        int materialBits = packMaterialBits();

        activeCircles.removeIf(instance -> {
            double dx = instance.x - camera.position().x;
            double dy = instance.y - camera.position().y;
            double dz = instance.z - camera.position().z;
            if (dx * dx + dy * dy + dz * dz > cullDistanceSq) return true;

            float rawProgress = (now - instance.timestamp) / (float) lifeMs * timeScale.getValue();
            if (rawProgress >= 1f) return true;
            float progress = clamp(rawProgress, 0f, 1f);

            float radius = maxRadius.getValue() * instance.impact;
            if (instance.type == JumpType.LANDING_HEAVY) radius *= heavyScale.getValue();
            float halfSize = radius * 1.34f;
            double y = instance.y + heightOffset.getValue();

            if (shadowOpacity.getValue() > 0.001f) {
                JumpCircleRenderer.drawShadowDecal(
                        event.poseStack(), instance.x, y, instance.z,
                        halfSize * shadowSize.getValue(),
                        packProgress(shadowColor.getColor(), progress),
                        shadowOpacity.getValue()
                );
            }

            int eventBits = instance.type.shaderId << 16;
            JumpCircleRenderer.drawJumpCircle(
                    event.poseStack(), instance.x, y, instance.z, halfSize,
                    packProgress(typeColor(instance.type), progress),
                    styleBits | eventBits, materialBits
            );

            if (dualTone.enabled) {
                JumpCircleRenderer.drawJumpCircle(
                        event.poseStack(), instance.x, y + 0.0015, instance.z, halfSize,
                        packProgress(accentColor.getColor(), progress),
                        accentBits | eventBits, materialBits
                );
            }
            return false;
        });
    }

    private int packStyleBits(boolean accentLayer) {
        int bits = style.index & 0x7;
        bits |= (colorFlow.index & 0x3) << 3;
        bits |= ((layers.getValue() - 1) & 0x3) << 5;
        bits |= ((ringCount.getValue() - 1) & 0x3) << 7;
        bits |= (quality.index & 0x3) << 9;
        bits |= (runeDetail.getValue() & 0x3) << 11;
        bits |= (particleDensity.getValue() & 0x3) << 13;
        if (spikes.enabled) bits |= 1 << 15;
        if (accentLayer) bits |= 1 << 18;
        if (shockwave.enabled) bits |= 1 << 19;
        bits |= (easing.index & 0x3) << 20;
        return bits;
    }

    private int packMaterialBits() {
        int bits = quantize(thickness.getValue(), 0.15f, 2.5f);
        bits |= quantize(glow.getValue(), 0f, 2.5f) << 4;
        bits |= quantize(distortion.getValue(), 0f, 1.5f) << 8;
        bits |= quantize(spin.getValue(), -3f, 3f) << 12;
        bits |= quantize(opacity.getValue(), 0.05f, 1f, 7) << 16;
        bits |= quantize(brightness.getValue(), 0.25f, 2.5f, 7) << 19;
        return bits;
    }

    private int typeColor(JumpType type) {
        return switch (type) {
            case TAKEOFF -> takeoffColor.getColor();
            case LANDING_NORMAL -> normalColor.getColor();
            case LANDING_HEAVY -> heavyColor.getColor();
        };
    }

    private void applyPreset() {
        if (applyingPreset || preset.is("Custom")) return;
        applyingPreset = true;
        try {
            if (preset.is("Cyber")) {
                style.setMode("Cyber");
                colorFlow.setMode("Pulse");
                takeoffColor.setColor(0xFF5CF8FF);
                normalColor.setColor(0xFF40B9FF);
                heavyColor.setColor(0xFFFF3B92);
                accentColor.setColor(0xFFFFFFFF);
                thickness.setValue(0.48f);
                glow.setValue(1.45f);
                distortion.setValue(0.08f);
                spin.setValue(1.6f);
                layers.setValue(2);
                ringCount.setValue(3);
                runeDetail.setValue(1);
                particleDensity.setValue(1);
                spikes.setEnabled(false);
            } else if (preset.is("Celestial")) {
                style.setMode("Celestial");
                colorFlow.setMode("Gradient");
                takeoffColor.setColor(0xFF8DBBFF);
                normalColor.setColor(0xFF7EE7FF);
                heavyColor.setColor(0xFFD994FF);
                accentColor.setColor(0xFFFFF1B8);
                thickness.setValue(0.62f);
                glow.setValue(1.7f);
                distortion.setValue(0.18f);
                spin.setValue(0.65f);
                layers.setValue(4);
                ringCount.setValue(2);
                runeDetail.setValue(2);
                particleDensity.setValue(3);
                spikes.setEnabled(true);
            } else if (preset.is("Inferno")) {
                style.setMode("Inferno");
                colorFlow.setMode("Pulse");
                takeoffColor.setColor(0xFFFFB12B);
                normalColor.setColor(0xFFFF7A24);
                heavyColor.setColor(0xFFFF301B);
                accentColor.setColor(0xFFFFF0A4);
                thickness.setValue(1.2f);
                glow.setValue(1.8f);
                distortion.setValue(1.1f);
                spin.setValue(1.2f);
                layers.setValue(3);
                ringCount.setValue(2);
                runeDetail.setValue(0);
                particleDensity.setValue(3);
                spikes.setEnabled(true);
            } else if (preset.is("Void")) {
                style.setMode("Void");
                colorFlow.setMode("Gradient");
                takeoffColor.setColor(0xFF6C42E8);
                normalColor.setColor(0xFF7D65FF);
                heavyColor.setColor(0xFFD82778);
                accentColor.setColor(0xFFDDC7FF);
                shadowColor.setColor(0xFF05030C);
                thickness.setValue(0.75f);
                glow.setValue(0.85f);
                distortion.setValue(0.85f);
                spin.setValue(-1.1f);
                layers.setValue(4);
                ringCount.setValue(3);
                runeDetail.setValue(3);
                particleDensity.setValue(2);
                spikes.setEnabled(true);
            } else {
                style.setMode("Arcane");
                colorFlow.setMode("Gradient");
                takeoffColor.setColor(0xFFB69CFF);
                normalColor.setColor(0xFF7DEBFF);
                heavyColor.setColor(0xFFFF5A36);
                accentColor.setColor(0xFFFFD77D);
                shadowColor.setColor(0xFF160C2A);
                thickness.setValue(0.82f);
                glow.setValue(1.15f);
                distortion.setValue(0.35f);
                spin.setValue(1f);
                layers.setValue(3);
                ringCount.setValue(2);
                runeDetail.setValue(2);
                particleDensity.setValue(2);
                spikes.setEnabled(true);
            }
        } finally {
            applyingPreset = false;
        }
    }

    private static int quantize(float value, float min, float max) {
        return Math.round(clamp((value - min) / (max - min), 0f, 1f) * 15f);
    }

    private static int quantize(float value, float min, float max, int levels) {
        return Math.round(clamp((value - min) / (max - min), 0f, 1f) * levels);
    }

    private static int packProgress(int argb, float progress) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = Math.round(clamp(progress, 0f, 1f) * 255f);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
