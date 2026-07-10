package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.impl.visual.KillEffect;
import geminiclient.gemini.modules.impl.visual.FullLight;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final
    private final GameRenderState gameRenderState = new GameRenderState();

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    public void inject2D(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        int i = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int j = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(this.minecraft, this.gameRenderState.guiRenderState, i, j);
        Gemini.eventManager.call(new Render2DEvent(g, g.pose()));
        CustomFontRenderer.flushAllPages();
    }

    /**
     * Post-processing injection — runs after 3D scene renders but before the GUI.
     *
     * Pass chain: Bright → BlurH → BlurV → Composite → Distortion → GodRay → Chromatic
     *             → (BH Center|Glow Flash|Shockwave) → ACES
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    public void injectPostProcess(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        KillEffect killEffect = Gemini.moduleManager.getModule(KillEffect.class);
        if (killEffect == null || !killEffect.enabled || !killEffect.hasActiveEffects()) return;

        long nowMs = System.currentTimeMillis();
        int stage = killEffect.getPrimaryStage(nowMs);
        int mergeCount = killEffect.getPrimaryMergeCount();
        double[] center = killEffect.getPrimaryEffectCenter();

        // AoE merge multiplier: stacked kills get enhanced post-processing
        float mergeMult = 1.0f + (mergeCount - 1) * 0.3f; // +30% per additional kill

        // ── Global fade-in: prevent sudden screen brightening ─────────
        // Ramps from 0 to 1 over the first 0.5s of the effect, so bloom
        // and other post-processing don't hit the screen suddenly.
        float effectAgeSec = (nowMs - killEffect.getPrimaryStartTime()) / 1000f;
        float globalFadeIn = Math.min(effectAgeSec / 0.5f, 1f);
        if (stage <= KillEffectInstance.STAGE_MAGIC_TOWER) {
            // During early stages (circle + tower), fade in even slower
            globalFadeIn = Math.min(effectAgeSec / 0.8f, 1f);
        }

        // ── Compute per-stage post-processing strengths ──────────
        float progress   = killEffect.getPrimaryProgress(nowMs);
        float bloom      = killEffect.getBloomStrength() * mergeMult * 1.4f;
        float distort    = 0f;
        float godRay     = 0f;
        float chromatic  = 0f;
        float radius     = 12f * bloom;

        // ── Black hole screen-space / flash / shockwave params ────
        int   bhStage     = 0;
        float bhProgress  = 0f;  // repurposed as preExpansionR / shockStrength
        float bhIntensity = 0f;  // repurposed as flashIntensity / shockSpeed

        if (stage >= KillEffectInstance.STAGE_BLACK_HOLE
                && stage <= KillEffectInstance.STAGE_COLLAPSE) {
            // ── Black hole phases: gravitational lensing ──────────
            distort  = 0.7f;
            godRay   = 0.3f;
            chromatic = 0.15f;

            // Distortion decays during collapse as the hole vanishes
            if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                distort *= 1.0f - progress * 0.9f;
            }

            bhStage     = stage;
            bhProgress  = progress;
            bhIntensity = mergeMult;

        } else if (stage == KillEffectInstance.STAGE_FLASH) {
            // ── Glow flash: multi-pulse light emission after void ──
            bloom    = Math.max(bloom, 2.5f);
            chromatic = 0.8f;
            godRay   = 1.0f;
            radius   = 30f;

            // Flash intensity: bell-curve pulse
            float t = progress;
            float flashIntensity = t < 0.4f
                ? (float)Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.04f)
                : (float)Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.12f);

            bhStage     = 7;
            bhProgress  = progress;                     // → preExpansionRadius
            bhIntensity = Math.max(flashIntensity, 0f); // → flashIntensity

        } else if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            // ── Hypernova explosion (enhanced) ────────────────────
            distort  = 0.6f;
            godRay   = 1.2f;
            chromatic = 0.5f;
            bloom    = Math.max(bloom, 2.0f);
            radius   = 24f;

            bhStage     = 8;
            bhProgress  = 0.6f * (1f - progress * 0.7f) * mergeMult;  // → shockStrength
            bhIntensity = 1.0f + progress * 2.5f;                     // → shockSpeed

        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            // ── Afterglow: all effects decay to zero ─────────────────
            // Quadratic ease-out decay: starts fast, slows near end
            float decay = 1.0f - progress;
            decay = decay * decay;

            float peakBloom = Math.max(killEffect.getBloomStrength(), 1.5f);
            bloom    = peakBloom * decay;
            distort  = 0.5f * decay;
            godRay   = 1.0f * decay;
            chromatic = 0.3f * decay;
            radius   = 16f * decay;

            // No black hole / flash / shockwave passes during afterglow
            bhStage     = 0;
            bhProgress  = 0f;
            bhIntensity = 0f;

        } else if (stage == KillEffectInstance.STAGE_FADE_OUT) {
            // ── Fade-out: post-processing already at zero; keep off ──
            // while the 3D intersecting planes (billboard + horizontal +
            // vertical cross) dissolve gracefully via the fade-out alpha.
            bloom     = 0f;
            distort   = 0f;
            godRay    = 0f;
            chromatic = 0f;
            radius    = 0f;

            bhStage     = 0;
            bhProgress  = 0f;
            bhIntensity = 0f;
        }

        KillEffectPostProcessor.processFrame(
                bloom * globalFadeIn, 0.35f,
                distort * globalFadeIn, godRay * globalFadeIn,
                chromatic * globalFadeIn, radius * globalFadeIn,
                center, null,
                bhStage, bhProgress, bhIntensity);
    }

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionScale(LivingEntity camera, float a, CallbackInfoReturnable<Float> cir) {
        FullLight module = Gemini.moduleManager.getModule(FullLight.class);
        if (module.enabled) {
            cir.setReturnValue(1f);
            cir.cancel();
        }
    }
}
