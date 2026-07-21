package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.TrailRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.modules.impl.visual.trail.TrailPoint;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A highly configurable procedural energy ribbon.
 *
 * <p>All ornamentation is generated in the fragment shader, so presets and
 * custom palettes do not require external textures. Geometry stays compact:
 * one camera-aware ribbon and one draw submission per frame.</p>
 */
public class Trail extends Module {

    // Look
    private final ListValue preset = new ListValue("Preset", "Celestial",
            new String[]{"Celestial", "Arcane", "Cyber", "Inferno", "Void", "Custom"});
    private final ListValue style = new ListValue("Style", "Aurora",
            new String[]{"Aurora", "Arcane", "Cyber", "Inferno", "Void"});
    private final ListValue colorMode = new ListValue("Color Mode", "Gradient",
            new String[]{"Gradient", "Rainbow", "Pulse", "Static"});
    private final ColorValue primaryColor = new ColorValue("Primary Color", 0xFF79E8FF);
    private final ColorValue secondaryColor = new ColorValue("Secondary Color", 0xFF9B6CFF);
    private final ColorValue accentColor = new ColorValue("Accent Color", 0xFFFFF1B8);

    // Geometry
    private final ListValue orientation = new ListValue("Orientation", "Billboard",
            new String[]{"Billboard", "Vertical", "Horizontal"});
    private final FloatValue width = new FloatValue("Width", 0.34f, 0.03f, 1.8f);
    private final FloatValue tailWidth = new FloatValue("Tail Width", 0.08f, 0.0f, 1.0f);
    private final FloatValue heightOffset = new FloatValue("Height Offset", 0.92f, 0.0f, 2.2f);
    private final FloatValue waveAmount = new FloatValue("Wave Amount", 0.08f, 0.0f, 0.8f);
    private final FloatValue waveFrequency = new FloatValue("Wave Frequency", 2.2f, 0.2f, 8.0f,
            () -> waveAmount.getValue() > 0.001f);

    // Timing and capture
    private final FloatValue life = new FloatValue("Life", 1.15f, 0.1f, 5.0f);
    private final IntValue maxPoints = new IntValue("Max Points", 120, 8, 320);
    private final IntValue captureInterval = new IntValue("Capture Interval", 1, 1, 8);
    private final BoolValue trailWhileIdle = new BoolValue("Trail While Idle", false);
    private final FloatValue minMovement = new FloatValue("Min Movement", 0.012f, 0.0f, 0.2f,
            () -> !trailWhileIdle.enabled);

    // Procedural material
    private final FloatValue opacity = new FloatValue("Opacity", 0.88f, 0.02f, 1.0f);
    private final FloatValue brightness = new FloatValue("Brightness", 1.35f, 0.1f, 3.0f);
    private final FloatValue glow = new FloatValue("Glow", 1.4f, 0.0f, 2.5f);
    private final FloatValue coreWidth = new FloatValue("Core Width", 0.34f, 0.04f, 1.0f);
    private final FloatValue edgeGlow = new FloatValue("Edge Glow", 0.7f, 0.0f, 2.0f);
    private final FloatValue distortion = new FloatValue("Distortion", 0.28f, 0.0f, 1.5f);
    private final FloatValue detailScale = new FloatValue("Detail Scale", 1.0f, 0.25f, 4.0f);
    private final FloatValue sparkleDensity = new FloatValue("Sparkles", 0.55f, 0.0f, 2.0f);
    private final FloatValue pulseStrength = new FloatValue("Pulse", 0.35f, 0.0f, 1.5f);
    private final FloatValue flowSpeed = new FloatValue("Flow Speed", 1.0f, -3.0f, 3.0f);

    // Rendering and performance
    private final ListValue blendMode = new ListValue("Blend", "Additive",
            new String[]{"Additive", "Soft"});
    private final BoolValue throughWalls = new BoolValue("Through Walls", false);
    private final FloatValue renderDistance = new FloatValue("Render Distance", 48.0f, 8.0f, 160.0f);
    private final ListValue quality = new ListValue("Quality", "High",
            new String[]{"Low", "Medium", "High", "Ultra"});

    private final List<TrailPoint> points = new ArrayList<>();
    private int tickCounter;
    private boolean applyingPreset;

    public Trail() {
        super("Trail", ModuleEnum.Visual);
        addValue(
                preset, style, colorMode, primaryColor, secondaryColor, accentColor,
                orientation, width, tailWidth, heightOffset, waveAmount, waveFrequency,
                life, maxPoints, captureInterval, trailWhileIdle, minMovement,
                opacity, brightness, glow, coreWidth, edgeGlow, distortion,
                detailScale, sparkleDensity, pulseStrength, flowSpeed,
                blendMode, throughWalls, renderDistance, quality
        );
        preset.setOnChange(this::applyPreset);
    }

    @Override
    public void onEnabled() {
        points.clear();
        tickCounter = 0;
    }

    @Override
    public void onDisabled() {
        points.clear();
        tickCounter = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        final float dt = 0.05f;
        for (TrailPoint point : points) point.tick(dt);
        points.removeIf(TrailPoint::expired);

        Vec3 velocity = mc.player.getDeltaMovement();
        boolean moving = velocity.horizontalDistanceSqr()
                > minMovement.getValue() * minMovement.getValue();

        tickCounter++;
        if (tickCounter < captureInterval.getValue()) return;
        tickCounter = 0;

        if (!moving && !trailWhileIdle.enabled) return;

        Vec3 position = mc.player.position().add(0.0, heightOffset.getValue(), 0.0);
        if (!points.isEmpty() && points.getFirst().pos.distanceToSqr(position) < 1.0e-6) return;

        points.addFirst(new TrailPoint(position, life.getValue()));
        while (points.size() > maxPoints.getValue()) points.removeLast();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || points.size() < 2) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;
        double maxDistance = renderDistance.getValue();
        if (camera.position().distanceToSqr(points.getFirst().pos) > maxDistance * maxDistance) return;

        TrailRenderer.draw(event.poseStack(), points, new TrailRenderer.TrailConfig(
                primaryColor.getColor(),
                secondaryColor.getColor(),
                accentColor.getColor(),
                style.index,
                colorMode.index,
                orientation.index,
                quality.index,
                width.getValue(),
                tailWidth.getValue(),
                waveAmount.getValue(),
                waveFrequency.getValue(),
                opacity.getValue(),
                brightness.getValue(),
                glow.getValue(),
                coreWidth.getValue(),
                edgeGlow.getValue(),
                distortion.getValue(),
                detailScale.getValue(),
                sparkleDensity.getValue(),
                pulseStrength.getValue(),
                flowSpeed.getValue(),
                blendMode.is("Soft"),
                throughWalls.enabled
        ));
    }

    private void applyPreset() {
        if (applyingPreset || preset.is("Custom")) return;
        applyingPreset = true;
        try {
            if (preset.is("Arcane")) {
                style.setMode("Arcane");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFF9C72FF);
                secondaryColor.setColor(0xFF45D9FF);
                accentColor.setColor(0xFFFFE6A6);
                width.setValue(0.38f);
                tailWidth.setValue(0.05f);
                waveAmount.setValue(0.12f);
                waveFrequency.setValue(2.8f);
                glow.setValue(1.55f);
                coreWidth.setValue(0.24f);
                edgeGlow.setValue(0.85f);
                distortion.setValue(0.42f);
                detailScale.setValue(1.15f);
                sparkleDensity.setValue(0.8f);
                pulseStrength.setValue(0.4f);
                flowSpeed.setValue(0.85f);
            } else if (preset.is("Cyber")) {
                style.setMode("Cyber");
                colorMode.setMode("Pulse");
                primaryColor.setColor(0xFF00F0FF);
                secondaryColor.setColor(0xFF1261FF);
                accentColor.setColor(0xFFFF3ED1);
                width.setValue(0.28f);
                tailWidth.setValue(0.18f);
                waveAmount.setValue(0.025f);
                waveFrequency.setValue(5.2f);
                glow.setValue(1.2f);
                coreWidth.setValue(0.18f);
                edgeGlow.setValue(1.25f);
                distortion.setValue(0.08f);
                detailScale.setValue(1.8f);
                sparkleDensity.setValue(0.35f);
                pulseStrength.setValue(0.85f);
                flowSpeed.setValue(1.55f);
            } else if (preset.is("Inferno")) {
                style.setMode("Inferno");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFFFFD33D);
                secondaryColor.setColor(0xFFFF4B17);
                accentColor.setColor(0xFFFFF5C4);
                width.setValue(0.46f);
                tailWidth.setValue(0.0f);
                waveAmount.setValue(0.16f);
                waveFrequency.setValue(3.6f);
                glow.setValue(1.85f);
                coreWidth.setValue(0.4f);
                edgeGlow.setValue(0.45f);
                distortion.setValue(0.95f);
                detailScale.setValue(1.25f);
                sparkleDensity.setValue(1.15f);
                pulseStrength.setValue(0.65f);
                flowSpeed.setValue(1.3f);
            } else if (preset.is("Void")) {
                style.setMode("Void");
                colorMode.setMode("Pulse");
                primaryColor.setColor(0xFF442080);
                secondaryColor.setColor(0xFF090315);
                accentColor.setColor(0xFFE358FF);
                width.setValue(0.52f);
                tailWidth.setValue(0.12f);
                waveAmount.setValue(0.2f);
                waveFrequency.setValue(1.6f);
                glow.setValue(0.9f);
                coreWidth.setValue(0.55f);
                edgeGlow.setValue(1.8f);
                distortion.setValue(0.75f);
                detailScale.setValue(0.7f);
                sparkleDensity.setValue(1.35f);
                pulseStrength.setValue(1.0f);
                flowSpeed.setValue(-0.65f);
            } else {
                style.setMode("Aurora");
                colorMode.setMode("Gradient");
                primaryColor.setColor(0xFF79E8FF);
                secondaryColor.setColor(0xFF9B6CFF);
                accentColor.setColor(0xFFFFF1B8);
                width.setValue(0.34f);
                tailWidth.setValue(0.08f);
                waveAmount.setValue(0.08f);
                waveFrequency.setValue(2.2f);
                glow.setValue(1.4f);
                coreWidth.setValue(0.34f);
                edgeGlow.setValue(0.7f);
                distortion.setValue(0.28f);
                detailScale.setValue(1.0f);
                sparkleDensity.setValue(0.55f);
                pulseStrength.setValue(0.35f);
                flowSpeed.setValue(1.0f);
            }
        } finally {
            applyingPreset = false;
        }
    }
}
