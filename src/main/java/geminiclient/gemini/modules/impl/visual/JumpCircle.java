package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.JumpCircleRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class JumpCircle extends Module {

    // ── Config ──────────────────────────────────────────────────

    private final ColorValue ringColor     = new ColorValue("Ring Color", 0xFFFFFFFF);
    private final FloatValue maxRadius     = new FloatValue("Max Radius", 1.5f, 0.5f, 5.0f);
    private final IntValue   duration      = new IntValue("Duration", 600, 200, 2000);
    private final FloatValue shadowOpacity = new FloatValue("Shadow", 0.3f, 0.0f, 1.0f);

    // ── State ───────────────────────────────────────────────────

    private final List<JumpInstance> activeCircles = new ArrayList<>();
    private boolean wasOnGround = true;
    private float peakFallDistance;

    // ── Types ───────────────────────────────────────────────────

    private enum JumpType {
        TAKEOFF,
        LANDING_NORMAL,
        LANDING_HEAVY;

        JumpCircleRenderer.RingType toRingType() {
            return switch (this) {
                case TAKEOFF         -> JumpCircleRenderer.RingType.JUMP;
                case LANDING_NORMAL  -> JumpCircleRenderer.RingType.LANDING_NORMAL;
                case LANDING_HEAVY   -> JumpCircleRenderer.RingType.LANDING_HEAVY;
            };
        }
    }

    private record JumpInstance(double x, double y, double z, long timestamp, JumpType type) {}

    // ── Constructor ─────────────────────────────────────────────

    public JumpCircle() {
        super("JumpCircle", ModuleEnum.Visual);
        addValue(ringColor, maxRadius, duration, shadowOpacity);
    }

    @Override
    public void onDisabled() {
        activeCircles.clear();
    }

    // ── Jump detection (takeoff) ────────────────────────────────

//    @EventTarget
//    public void onJump(JumpEvent event) {
//        if (mc.player == null || mc.level == null) return;
//
//        Vec3 pos = mc.player.position();
//        activeCircles.add(new JumpInstance(pos.x, pos.y, pos.z,
//                System.currentTimeMillis(), JumpType.TAKEOFF));
//    }

    // ── Landing detection ───────────────────────────────────────

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (event.getTimeEnum() != TimeEnum.Pre) return;

        boolean onGround = mc.player.onGround();

        if (!onGround) {
            peakFallDistance = (float) Math.max(peakFallDistance, mc.player.fallDistance);
        }

        // Detect air → ground transition
        if (!wasOnGround && onGround && peakFallDistance > 0.1f) {
            JumpType type = peakFallDistance >= 3.0f
                    ? JumpType.LANDING_HEAVY
                    : JumpType.LANDING_NORMAL;

            Vec3 pos = mc.player.position();
            activeCircles.add(new JumpInstance(pos.x, pos.y, pos.z,
                    System.currentTimeMillis(), type));
        }

        if (onGround) {
            peakFallDistance = 0f;
        }
        wasOnGround = onGround;
    }

    // ── Rendering ───────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || activeCircles.isEmpty()) return;

        long  now        = System.currentTimeMillis();
        int   dur        = duration.getValue();
        float radius     = maxRadius.getValue();
        int   themeColor = ringColor.getColor();
        float shadow     = shadowOpacity.getValue();

        activeCircles.removeIf(instance -> {
            float progress = (now - instance.timestamp) / (float) dur;
            if (progress >= 1f) return true;

            float quadHalfSize = radius * 2.2f;
            double groundY = JumpCircleRenderer.findGroundY(instance.x, instance.y, instance.z);

            // Shadow decal
            if (shadow > 0.001f) {
                int shadowABGR = packShadow(progress, shadow);
                float shadowScale = 1.3f + progress * 0.5f;
                JumpCircleRenderer.drawShadowDecal(event.poseStack(),instance.x, groundY, instance.z,
                        quadHalfSize * shadowScale, shadowABGR);
            }

            // Main ring
            int ringABGR = packProgress(typeColor(instance.type, themeColor), progress);
            JumpCircleRenderer.drawJumpCircle(event.poseStack(),instance.x, groundY, instance.z,
                    quadHalfSize, ringABGR, instance.type.toRingType());

            return false;
        });
    }

    // ── Colour packing ─────────────────────────────────────────

    /** Ring base colour (ARGB) per jump type. */
    private static int typeColor(JumpType type, int themeColor) {
        return switch (type) {
            case TAKEOFF         -> themeColor;      // user-configured
            case LANDING_NORMAL  -> 0xFFD0C8B8;      // gray-tan
            case LANDING_HEAVY   -> 0xFFFF6622;      // orange-red
        };
    }

    /**
     * Pack ring colour (ARGB → ABGR) with lifetime progress in the alpha byte.
     * The fragment shader reads vertexColour.a as normalised u_LifeTime (0→1).
     */
    private static int packProgress(int argb, float progress) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b =  argb        & 0xFF;
        int a = (int) (Math.min(Math.max(progress, 0f), 1f) * 255f);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** Pack shadow decal: progress in A, config opacity in G, black in R/B. */
    private static int packShadow(float progress, float shadowOpacity) {
        int a = (int) (Math.min(Math.max(progress, 0f), 1f) * 255f);
        int g = (int) (Math.min(Math.max(shadowOpacity, 0f), 1f) * 255f);
        return (a << 24) | (g << 8);  // R=0, G=opacity, B=0, A=progress
    }
}
