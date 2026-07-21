package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.KillEffect;
import geminiclient.gemini.modules.impl.visual.SweepingAttackVFX;
import geminiclient.gemini.modules.impl.visual.FullLight;
import geminiclient.gemini.modules.impl.visual.clickgui.AbstractClickGuiScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
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

    /**
     * While a ClickGui screen is open, override the blur radius used by the
     * vanilla menu-blur post chain for this frame. The field is re-extracted
     * from the game options every frame, so the original value is restored
     * automatically once the ClickGui closes.
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void applyClickGuiBlurRadius(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (this.minecraft.gui.screen() instanceof AbstractClickGuiScreen screen) {
            ClickGui clickGui = Gemini.moduleManager.getModule(ClickGui.class);
            int strength = clickGui != null ? clickGui.getBlurStrength() : 0;
            this.gameRenderState.optionsRenderState.menuBackgroundBlurriness =
                    Math.round(strength * screen.getBlurFade());
        }
    }

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    public void inject2D(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        int i = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int j = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(this.minecraft, this.gameRenderState.guiRenderState, i, j);
        Gemini.eventManager.call(new Render2DEvent(g, g.pose()));
        CustomFontRenderer.flushAllPages();

        // ── ClickGui: submit the screen above all HUD, with a blur boundary ──
        // Everything submitted so far (vanilla HUD + client HUD modules such as
        // the ArrayList) sits in strata before the boundary and gets blurred by
        // the vanilla blur pass; the ClickGui strata draw sharp on top of it.
        if (this.minecraft.gui.screen() instanceof AbstractClickGuiScreen screen
                && this.minecraft.gui.overlay() == null) {
            GuiRenderState guiState = this.gameRenderState.guiRenderState;
            guiState.nextStratum();

            ClickGui clickGui = Gemini.moduleManager.getModule(ClickGui.class);
            if (clickGui != null && clickGui.getBlurStrength() > 0) {
                guiState.blurBeforeThisStratum();
            }

            GuiGraphicsExtractor screenGraphics =
                    new GuiGraphicsExtractor(this.minecraft, guiState, i, j);
            screen.extractRenderStateWithTooltipAndSubtitles(
                    screenGraphics, i, j, deltaTracker.getGameTimeDeltaTicks());
            CustomFontRenderer.flushAllPages();
        }
    }

    /**
     * Post-processing injection — runs after 3D scene renders but before the GUI.
     *
     * Pass chain: Bright → BlurH → BlurV → Composite → Distortion → GodRay → Chromatic
     *             → (BH Center|Glow Flash|Shockwave) → ACES
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
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
        // smoothstep (3t²−2t³) ramp: zero slope at both ends, so bloom /
        // distortion / tone mapping ease in instead of snapping on.
        float effectAgeSec = (nowMs - killEffect.getPrimaryStartTime()) / 1000f;
        float fadeT = Math.min(effectAgeSec / 0.9f, 1f);
        float globalFadeIn = fadeT * fadeT * (3f - 2f * fadeT);

        // ── Compute per-stage post-processing strengths ──────────
        float progress   = killEffect.getPrimaryProgress(nowMs);
        float bloom      = killEffect.getBloomStrength() * mergeMult * 1.4f;
        float distort    = 0f;
        float godRay     = 0f;
        float chromatic  = 0f;
        float radius     = 12f * bloom;

        // ── Black hole screen-space / flash / shockwave params ────
        int   bhStage     = 0;
        float bhProgress  = 0f;  // BH: stage progress;  Nova: hypernova progress
        float bhIntensity = 0f;  // BH: merge mult;      Flash: bell pulse

        if (stage >= KillEffectInstance.STAGE_BLACK_HOLE
                && stage <= KillEffectInstance.STAGE_COLLAPSE) {
            // ── Black hole phases: gravitational lensing ──────────
            // Entry ramp only on stage 3 (first BH stage): later BH stages
            // share the same strengths, so ramping them would dip.
            float entry = 1f;
            if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
                entry = smoothstep01(progress / 0.15f);
            }
            distort  = 0.85f * entry;
            godRay   = 0.45f * entry;
            chromatic = 0.20f * entry;

            // Distortion decays during collapse as the hole vanishes;
            // god rays and chromatic also wind down to zero so nothing
            // pops when the silent VOID stage begins.
            if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                distort *= 1.0f - progress * 0.9f;
                godRay   *= 1.0f - progress;
                chromatic *= 1.0f - progress;
            }

            bhStage     = stage;
            bhProgress  = progress;
            bhIntensity = mergeMult;

        } else if (stage == KillEffectInstance.STAGE_FLASH) {
            // ── Glow flash: multi-pulse light emission after void ──
            float entry = smoothstep01(progress / 0.10f);
            bloom    = Math.max(bloom, 3.2f * entry);
            chromatic = 1.0f * entry;
            godRay   = 1.35f * entry;
            radius   = 36f * entry;

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
            // Blend from the flash into a sustained, more violent detonation.
            // interpolates instead of snapping down and back up.
            float entry = smoothstep01(progress / 0.05f);
            float pulse = 0.90f + 0.10f
                    * Math.abs((float)Math.sin(progress * Math.PI * 7.0f));
            bloom    = Math.max(bloom, (3.2f + (3.0f - 3.2f) * entry) * pulse);
            godRay   = 1.35f + (1.75f - 1.35f) * entry;
            chromatic = 1.0f + (0.72f - 1.0f) * entry;
            distort  = 0.88f * entry;
            radius   = 36f + (40f - 36f) * entry;

            bhStage     = 8;
            bhProgress  = progress;    // → SHOCKWAVE / FLASH_SCREEN / AFTERIMAGE progress
            bhIntensity = mergeMult;   // → intensity multiplier

        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            // ── Afterglow: decay FROM hypernova's end values ─────────
            // Start exactly where stage 8 left off so the boundary is
            // continuous; godRay keeps a 0.35 floor that the fade-out
            // stage then carries to zero.
            float d1 = 1.0f - progress;
            float decay = d1 * d1;

            bloom    = Math.max(killEffect.getBloomStrength() * mergeMult * 1.4f, 2.7f) * decay;
            distort  = 0.88f * decay;
            godRay   = 0.35f + 1.40f * decay;
            chromatic = 0.72f * decay;
            radius   = 40f * decay;

            // No black hole / flash / shockwave passes during afterglow
            bhStage     = 0;
            bhProgress  = 0f;
            bhIntensity = 0f;

        } else if (stage == KillEffectInstance.STAGE_FADE_OUT) {
            // ── Fade-out: residual shafts die with the glow ball ─────
            // godRay continues from afterglow's 0.35 floor through the
            // fade-out smoothstep; everything else is already at zero.
            float t = progress;
            float fade = 1f - t * t * (3f - 2f * t);
            bloom     = 0f;
            distort   = 0f;
            godRay    = 0.35f * fade;
            chromatic = 0f;
            radius    = 0f;

            bhStage     = 0;
            bhProgress  = 0f;
            bhIntensity = 0f;
        }

        // ── Residual light source for depth-aware passes ─────────
        // Pseudo ray-traced point light (screen-space) at the explosion
        // center. Intensity/color envelope per stage; occluded by blocks
        // and entities via depth-buffer shadow rays (see SCREEN_LIGHTING).
        // center[1] already includes the +1.5 visual-center offset.
        float[] lightWorldPos = null;
        float[] lightColor = null;
        float ssrIntensity = 0f;
        int volumetricSteps = 16;

        if (center != null) {
            float li = 0f;              // light intensity
            float lr = 28f;             // light radius (blocks)
            float lcR = 1f, lcG = 0.85f, lcB = 0.55f;

            if (stage >= KillEffectInstance.STAGE_BLACK_HOLE
                    && stage <= KillEffectInstance.STAGE_ACCRETION) {
                // Accretion disk glow: faint warm light while the hole feeds.
                // (Also feeds the volumetric god-ray pass its light position.)
                // smoothstep entry ramp on stage 3 so the lighting pass
                // doesn't snap on at the tower→BH boundary.
                float entry = (stage == KillEffectInstance.STAGE_BLACK_HOLE)
                        ? smoothstep01(progress / 0.15f) : 1f;
                li  = 0.75f * entry;
                lr  = 24f;
                lcR = 1f; lcG = 0.55f; lcB = 0.15f;
            } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                // Energy builds as the hole collapses, then dies with it —
                // fade the light out over the last 30% so the transition
                // into the silent VOID stage has no light pop.
                float dieOut = 1f - smoothstep01((progress - 0.7f) / 0.3f);
                li  = (0.75f + progress * 1.05f) * dieOut;
                lr  = 24f + progress * 10f;
                lcR = 1f; lcG = 0.60f; lcB = 0.20f;
            } else if (stage == KillEffectInstance.STAGE_FLASH) {
                // Pulse with the flash bell curve, white-hot
                li  = 2.8f * bhIntensity;
                lr  = 42f;
                lcR = 1f; lcG = 0.97f; lcB = 0.90f;
            } else if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                // Blinding at detonation, slow ease-out decay
                float entry = smoothstep01(progress / 0.05f);
                float stellarPulse = 0.90f + 0.10f
                        * Math.abs((float)Math.sin(progress * Math.PI * 7.0f));
                li  = 4.2f * (1f - progress * 0.35f) * entry * stellarPulse;
                lr  = 54f;
                lcR = 1f; lcG = 0.95f; lcB = 0.85f;
            } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
                // Residual ember starts at the sustained hypernova's endpoint.
                // fade-out stage's entry (li 0.5, lr 26, color 1.0/0.60/0.28).
                float d1 = 1.0f - progress;
                float decay = d1 * d1;
                li  = 0.55f + 1.91f * decay;
                lr  = 28f + 26f * decay;
                lcR = 1f; lcG = 0.60f + 0.35f * decay; lcB = 0.28f + 0.57f * decay;
            } else if (stage == KillEffectInstance.STAGE_FADE_OUT) {
                // Last ember dying out with the fade-out smoothstep —
                // starts at the afterglow floor (0.5) and falls to zero.
                float t = progress;
                float fade = 1f - t * t * (3f - 2f * t);
                li  = 0.55f * fade;
                lr  = 17f + 11f * fade;
                lcR = 1f; lcG = 0.50f + 0.10f * fade; lcB = 0.22f + 0.06f * fade;
            }
            // STAGE_VOID (6): li stays 0 — dead silence, no light.

            if (li > 0.01f) {
                lightWorldPos = new float[]{
                    (float) center[0],
                    (float) center[1] + 1.0f, // matches drawHypernova glow ball (+2.5 total)
                    (float) center[2],
                    lr
                };
                lightColor = new float[]{lcR, lcG, lcB, li};
                // SSRT only during flash/hypernova; fade it out across the
                // hypernova stage so it doesn't cut at the afterglow boundary.
                ssrIntensity = stage == KillEffectInstance.STAGE_FLASH ? 0.9f
                        : stage == KillEffectInstance.STAGE_HYPERNOVA
                        ? 0.9f * (1f - smoothstep01((progress - 0.35f) / 0.50f))
                        : 0f;
                if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                    volumetricSteps = 24;
                }
            }
        }

        // ── Chain fade: eases the whole post chain in AND out ──────
        // Ramps in over the first 0.9s (smoothstep); ramps back out over
        // the fade-out stage so the ACES tone-map/vignette never pops off
        // when the chain deactivates at the end of the effect.
        float chainFade = globalFadeIn;
        if (stage == KillEffectInstance.STAGE_FADE_OUT) {
            float t = progress;
            chainFade *= 1f - t * t * (3f - 2f * t);
        }

        KillEffectPostProcessor.processFrame(
                bloom * globalFadeIn, 0.35f,
                distort * globalFadeIn, godRay * globalFadeIn,
                chromatic * globalFadeIn, radius * globalFadeIn,
                center, null,
                bhStage, bhProgress, bhIntensity,
                lightWorldPos, lightColor,
                ssrIntensity, volumetricSteps,
                chainFade);

        // ── Sweep Attack post-processing (distortion + chromatic) ──
        SweepingAttackVFX sweep = Gemini.moduleManager.getModule(SweepingAttackVFX.class);
        if (sweep != null && sweep.enabled && sweep.hasActiveEffects()) {
            sweep.processPost();
        }
    }

    /**
     * smoothstep(0,1,x) — zero-slope ease at both ends. Used for stage-entry
     * ramps so post-processing passes never pop in at full strength.
     */
    private static float smoothstep01(float x) {
        float t = Math.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void overrideNightVisionScale(LivingEntity camera, float partialTick, CallbackInfoReturnable<Float> cir) {
        FullLight module = Gemini.moduleManager.getModule(FullLight.class);
        if (module.enabled) {
            cir.setReturnValue(1f);
            cir.cancel();
        }
    }
}
