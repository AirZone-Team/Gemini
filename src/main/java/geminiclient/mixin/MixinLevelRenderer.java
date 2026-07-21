package geminiclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.impl.visual.KillEffect;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /** Saved camera rotation before shake is applied — restored after 3D render. */
    private static final Quaternionf savedRotation = new Quaternionf();

    /**
     * Apply camera shake BEFORE the 3D level renders.
     *
     * <p>Modifies {@code Camera.rotation()} in-place with a noise-based offset.
     * The shake intensity is driven by {@link KillEffect#getShakeIntensity()},
     * which ramps up during collapse/flash/hypernova stages and decays over time.</p>
     *
     * <p>The view matrix is already baked by this point, so world geometry
     * won't shake.  However {@code KillEffectRenderer} reads
     * {@code cam.rotation()} directly for billboard math, so all kill-effect
     * billboards (magic circle, black hole, hypernova planes, particles) will
     * shake — which is the desired behavior.</p>
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onPreRenderLevel(GraphicsResourceAllocator resourceAllocator,
                                   DeltaTracker deltaTracker,
                                   boolean renderOutline,
                                   CameraRenderState cameraState,
                                   Matrix4fc modelViewMatrix,
                                   GpuBufferSlice terrainFog,
                                   Vector4f fogColor,
                                   boolean shouldRenderSky,
                                   CallbackInfo ci) {
        KillEffect killEffect = Gemini.moduleManager.getModule(KillEffect.class);
        if (killEffect == null || !killEffect.enabled) return;

        float shake = killEffect.getShakeIntensity();
        if (shake < 0.001f) return;

        var cam = Minecraft.getInstance().gameRenderer.mainCamera();
        Quaternionf rot = cam.rotation();

        // Save original rotation before modifying
        savedRotation.set(rot);

        // ── Organic camera shake via two sine waves at different frequencies ──
        // sin(a·t) * cos(b·t) gives a more natural, less regular shake than
        // simple sin waves — simulates the "ragged" feel of an explosion shockwave.
        double t = System.currentTimeMillis() / 1000.0;
        float clampedShake = Math.min(shake, 8.0f);

        float noiseYaw = (float)(Math.sin(t * 47.0 + 1.3) * Math.cos(t * 73.0 + 0.7))
                        * clampedShake * 0.012f;
        float noisePitch = (float)(Math.sin(t * 53.0 + 0.7) * Math.cos(t * 61.0 + 2.1))
                          * clampedShake * 0.010f;
        float noiseRoll = (float)(Math.sin(t * 41.0 + 2.5) * Math.cos(t * 67.0 + 1.9))
                         * clampedShake * 0.004f;

        // Apply shake as local-axis rotations on the camera quaternion
        rot.rotateLocalY(noiseYaw);
        rot.rotateLocalX(noisePitch);
        rot.rotateLocalZ(noiseRoll);
    }

    /**
     * Restore original camera rotation and fire {@link Render3DEvent}.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onPostRenderLevel(GraphicsResourceAllocator resourceAllocator,
                                  DeltaTracker deltaTracker,
                                  boolean renderOutline,
                                  CameraRenderState cameraState,
                                  Matrix4fc modelViewMatrix,
                                  GpuBufferSlice terrainFog,
                                  Vector4f fogColor,
                                  boolean shouldRenderSky,
                                  CallbackInfo ci) {
        // ── Restore camera rotation (undo shake) ─────────────────────
        KillEffect killEffect = Gemini.moduleManager.getModule(KillEffect.class);
        if (killEffect != null && killEffect.enabled && killEffect.getShakeIntensity() > 0.001f) {
            var cam = Minecraft.getInstance().gameRenderer.mainCamera();
            cam.rotation().set(savedRotation);
        }

        // ── Fire 3D render event for custom renderers ────────────────
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);
        Gemini.eventManager.call(new Render3DEvent(poseStack,
                deltaTracker.getGameTimeDeltaPartialTick(false)));
    }
}
