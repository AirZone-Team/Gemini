package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.modules.impl.visual.KillEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera shake for Hypernova Kill Effect.
 *
 * <p>Applies a per-frame random rotation offset to the camera when
 * the KillEffect module produces shake (black hole collapse + hypernova).
 * Injected at TAIL of {@link Camera#update(DeltaTracker)} after all vanilla
 * camera setup is complete.</p>
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow private float yRot;
    @Shadow private float xRot;
    @Shadow private float roll;

    @Shadow protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "update", at = @At("TAIL"))
    private void applyCameraShake(DeltaTracker deltaTracker, CallbackInfo ci) {
        KillEffect killEffect = Gemini.moduleManager.getModule(KillEffect.class);
        if (killEffect == null || !killEffect.enabled) return;

        float shake = killEffect.getShakeIntensity();
        if (shake < 0.001f) return;

        // Per-frame deterministic-but-random-enough shake
        long frameId = System.currentTimeMillis() / 16; // ~60fps frame index
        float seed1 = (frameId * 0x9E3779B9L) & 0xFFFFFFFFL;
        float seed2 = ((frameId + 127) * 0x9E3779B9L) & 0xFFFFFFFFL;

        float pitchShake = ((seed1 / 0xFFFFFFFFp0f) * 2f - 1f) * shake * 0.6f;
        float yawShake   = ((seed2 / 0xFFFFFFFFp0f) * 2f - 1f) * shake * 0.8f;

        // Clamp shake magnitude
        float maxShake = 8.0f;
        pitchShake = Math.clamp(pitchShake, -maxShake, maxShake);
        yawShake   = Math.clamp(yawShake, -maxShake, maxShake);

        // Apply shake offset on top of existing rotation, preserving roll
        this.setRotation(this.yRot + yawShake, this.xRot + pitchShake, this.roll);
    }
}
