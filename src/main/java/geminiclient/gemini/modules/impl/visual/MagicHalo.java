package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.MagicHaloRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.world.phys.Vec3;

/**
 * A fully procedural, highly configurable halo ornament.
 *
 * <p>Every style shares the same live geometry, material, palette and motion
 * controls. The fragment shader builds the complete ornament, so switching
 * styles or editing a color never recreates world geometry.</p>
 */
public class MagicHalo extends Module {

    // Style and palette
    private final ListValue style = new ListValue("Style", "Seraphic",
            new String[]{"Seraphic", "Arcane", "Cyber", "Void", "Inferno", "Frost", "Prism"});
    private final ListValue colorMode = new ListValue("Color Mode", "Style",
            new String[]{"Style", "Custom", "Rainbow"});
    private final ColorValue primaryColor = new ColorValue("Primary", 0xFFFFD978,
            () -> colorMode.is("Custom"));
    private final ColorValue secondaryColor = new ColorValue("Secondary", 0xFFFF6FD8,
            () -> colorMode.is("Custom"));
    private final ColorValue accentColor = new ColorValue("Accent", 0xFFF8FCFF,
            () -> colorMode.is("Custom"));
    private final FloatValue rainbowSpeed = new FloatValue("Rainbow Speed", 0.7f, 0.0f, 3.0f,
            () -> colorMode.is("Rainbow"));

    // Placement and silhouette
    private final FloatValue size = new FloatValue("Size", 1.15f, 0.3f, 3.5f);
    private final FloatValue heightOffset = new FloatValue("Height", 0.58f, -1.0f, 2.5f);
    private final FloatValue tilt = new FloatValue("Tilt", 0.0f, -45.0f, 45.0f);
    private final FloatValue ringRadius = new FloatValue("Ring Radius", 0.36f, 0.18f, 0.52f);
    private final FloatValue ringThickness = new FloatValue("Ring Thickness", 0.065f, 0.012f, 0.18f);
    private final IntValue spikeCount = new IntValue("Spike Count", 20, 4, 32);
    private final FloatValue spikeLength = new FloatValue("Spike Length", 0.30f, 0.0f, 0.58f);
    private final IntValue layers = new IntValue("Ring Layers", 3, 1, 5);

    // Ornament density
    private final IntValue runeDetail = new IntValue("Rune Detail", 2, 0, 3);
    private final IntValue particleDensity = new IntValue("Star Density", 2, 0, 3);
    private final BoolValue crown = new BoolValue("Crown", true);
    private final BoolValue runes = new BoolValue("Runes", true);
    private final BoolValue orbitals = new BoolValue("Orbitals", true);
    private final BoolValue starfield = new BoolValue("Starfield", true);

    // Material and animation
    private final FloatValue alpha = new FloatValue("Opacity", 0.92f, 0.05f, 1.0f);
    private final FloatValue intensity = new FloatValue("Brightness", 1.35f, 0.2f, 2.8f);
    private final FloatValue glow = new FloatValue("Glow", 1.45f, 0.0f, 3.0f);
    private final FloatValue sharpness = new FloatValue("Sharpness", 0.78f, 0.0f, 1.0f);
    private final FloatValue animSpeed = new FloatValue("Anim Speed", 1.0f, 0.0f, 3.0f);
    private final FloatValue rotation = new FloatValue("Rotation", 0.65f, -3.0f, 3.0f);
    private final FloatValue pulse = new FloatValue("Pulse", 0.45f, 0.0f, 1.5f);
    private final FloatValue distortion = new FloatValue("Distortion", 0.18f, 0.0f, 1.0f);

    private float elapsedTime;

    public MagicHalo() {
        super("MagicHalo", ModuleEnum.Visual);
        addValue(
                style, colorMode, primaryColor, secondaryColor, accentColor, rainbowSpeed,
                size, heightOffset, tilt, ringRadius, ringThickness, spikeCount, spikeLength, layers,
                runeDetail, particleDensity, crown, runes, orbitals, starfield,
                alpha, intensity, glow, sharpness,
                animSpeed, rotation, pulse, distortion
        );
    }

    @Override
    public void onDisabled() {
        elapsedTime = 0f;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        elapsedTime += 0.05f * animSpeed.getValue();
        if (elapsedTime > 3600f) elapsedTime -= 3600f;

        Vec3 pos = mc.player.getPosition(event.partialTick());
        double haloY = pos.y + mc.player.getEyeHeight() + heightOffset.getValue();

        int flags = 0;
        if (crown.enabled) flags |= MagicHaloRenderer.FLAG_CROWN;
        if (runes.enabled) flags |= MagicHaloRenderer.FLAG_RUNES;
        if (orbitals.enabled) flags |= MagicHaloRenderer.FLAG_ORBITALS;
        if (starfield.enabled) flags |= MagicHaloRenderer.FLAG_STARFIELD;

        MagicHaloRenderer.Settings settings = new MagicHaloRenderer.Settings(
                elapsedTime,
                style.index,
                colorMode.index,
                flags,
                primaryColor.getColor(),
                secondaryColor.getColor(),
                accentColor.getColor(),
                alpha.getValue(),
                intensity.getValue(),
                glow.getValue(),
                ringRadius.getValue(),
                ringThickness.getValue(),
                spikeLength.getValue(),
                spikeCount.getValue(),
                layers.getValue(),
                runeDetail.getValue(),
                particleDensity.getValue(),
                sharpness.getValue(),
                rotation.getValue(),
                pulse.getValue(),
                distortion.getValue(),
                rainbowSpeed.getValue(),
                tilt.getValue()
        );

        MagicHaloRenderer.draw(
                event.poseStack(),
                pos.x, haloY, pos.z,
                size.getValue(),
                settings
        );
    }
}
