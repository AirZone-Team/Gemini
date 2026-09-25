package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.customRenderer.glsl.CustomRendererRegistry;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectInstance;
import geminiclient.gemini.customRenderer.glsl.modules.KillEffectPostProcessor;
import geminiclient.gemini.customRenderer.glsl.modules.ThaumaturgyEffectInstance;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.FrameEvent;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.impl.visual.BlackHolePet;
import geminiclient.gemini.modules.impl.visual.ClickGui;
import geminiclient.gemini.modules.impl.visual.KillEffect;
import geminiclient.gemini.modules.impl.visual.SweepingAttackVFX;
import geminiclient.gemini.modules.impl.visual.FullLight;
import geminiclient.gemini.modules.impl.visual.clickgui.AbstractClickGuiScreen;
import geminiclient.gemini.modules.impl.visual.osu4k.screen.Osu4kScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
     * Real-time frame update: fires once per rendered frame, regardless of the
     * game tick rate — the frame-rate counterpart of UpdateEvent (per tick).
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void postFrameEvent(CallbackInfo ci) {
        Gemini.eventManager.post(EventTypes.FRAME,
                new FrameEvent(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }

    /**
     * Apply the appropriate global blur while a full-screen client UI is open.
     * The value is re-extracted every frame, so closing the screen restores the
     * normal game option automatically.
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void applyClickGuiBlurRadius(CallbackInfo ci) {
        if (this.minecraft.gui.screen() instanceof AbstractClickGuiScreen screen) {
            ClickGui clickGui = Gemini.moduleManager.getModule(ClickGui.class);
            int strength = clickGui != null ? clickGui.getBlurStrength() : 0;
            this.gameRenderState.optionsRenderState.menuBackgroundBlurriness =
                    Math.round(strength * screen.getBlurFade());
        } else if (this.minecraft.gui.screen() instanceof Osu4kScreen screen) {
            this.gameRenderState.optionsRenderState.menuBackgroundBlurriness =
                    Math.round(8f * screen.getBlurFade());
        }
    }

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    public void inject2D(CallbackInfo ci) {
        if (!CustomRendererRegistry.areShadersReady()) {
            return;
        }
        // 全屏 Overlay（启动加载画面等）期间 HUD/RENDER_2D 不参与：
        // Overlay 走独立 strata，HUD 模块会泄漏绘制到加载画面之上。
        if (this.minecraft.gui.overlay() != null) {
            return;
        }

        int i = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int j = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(this.minecraft, this.gameRenderState.guiRenderState, i, j);
        Gemini.eventManager.post(EventTypes.RENDER_2D, new Render2DEvent(g, g.pose()));
        CustomFontRenderer.flushPendingGlyphs();

        // Submit full-screen client UIs above the HUD with one blur boundary.
        // World, vanilla HUD and client HUD modules remain below and are blurred;
        // the active OSU4K/ClickGui screen is extracted sharply on top.
        if ((this.minecraft.gui.screen() instanceof AbstractClickGuiScreen
                || this.minecraft.gui.screen() instanceof Osu4kScreen) && this.minecraft.gui.overlay() == null) {
            GuiRenderState guiState = this.gameRenderState.guiRenderState;
            guiState.nextStratum();
            boolean blur = true;
            if (this.minecraft.gui.screen() instanceof AbstractClickGuiScreen clickScreen) {
                ClickGui clickGui = Gemini.moduleManager.getModule(ClickGui.class);
                blur = clickGui != null && clickGui.getBlurStrength() > 0;
            }
            if (blur) {
                guiState.blurBeforeThisStratum();
            }

            GuiGraphicsExtractor screenGraphics =
                    new GuiGraphicsExtractor(this.minecraft, guiState, i, j);
            if (this.minecraft.gui.screen() instanceof AbstractClickGuiScreen clickScreen) {
                clickScreen.extractRenderStateWithTooltipAndSubtitles(
                        screenGraphics, i, j, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            } else if (this.minecraft.gui.screen() instanceof Osu4kScreen osuScreen) {
                osuScreen.extractRenderStateWithTooltipAndSubtitles(
                        screenGraphics, i, j, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
            }
            CustomFontRenderer.flushPendingGlyphs();
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
    public void injectPostProcess(CallbackInfo ci) {
        KillEffect killEffect = Gemini.moduleManager.getModule(KillEffect.class);
        if (killEffect != null && killEffect.enabled && killEffect.hasActiveEffects()) {
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
        float hellFade   = killEffect.getHellChainFade(nowMs); // 1.0 unless Hell Hand mode is active
        float thaumFade  = killEffect.getThaumChainFade(nowMs); // the spell's own master fade
        boolean hellMode = stage < 0 && killEffect.isHellHandMode();

        // ── Thaumaturgy Strike runs a chain of its own ────────────
        // The Hypernova chain is built for a detonation that consumes the
        // screen: filmic grade, god rays, lensing, a residual point light. A
        // sky sigil is line art, and all of that at once hazes the frame into
        // one white blob. In this mode the explosion chain is switched off
        // outright and only a tight bloom is driven, from the instance's own
        // envelopes — the stage constants below are Hypernova-specific.
        ThaumaturgyEffectInstance thaum =
                (stage < 0 && !hellMode && killEffect.isThaumaturgyMode())
                        ? killEffect.getPrimaryThaumEffect() : null;
        if (thaum != null && thaum.currentStage(nowMs) < 0) {
            thaum = null;
        }
        float hyperFade = thaum == null ? 1f : 0f;

        float bloom      = killEffect.getBloomStrength() * mergeMult * 1.4f
                         * hellFade * hyperFade;
        float distort    = 0f;
        float godRay     = 0f;
        float chromatic  = 0f;

        // ── Hell Hand: keep the glow local ────────────────────────
        // The Hypernova chain is tuned for a screen-consumed detonation.
        // Hell Hand only tears a small rift open, so a whole-screen bloom
        // (soft threshold + 14-block torch halo) hazes the entire frame.
        // Raise the luminance threshold to only catch the rift's molten
        // material, soften the strength, and gate the bright-pass
        // extraction by screen-space distance to the rift (bloomFalloff).
        float threshold    = 0.35f;
        float bloomFalloff = 0f;
        if (hellMode) {
            bloom        *= 0.55f;
            threshold     = 0.72f;
            bloomFalloff  = 1.1f;
        }
        float radius     = 12f * bloom;

        // ── The summoning array is line art, like the sky sigil ─────
        // At the detonation's threshold every gold stroke in the engraving
        // clears the bright pass, and the blur radius is wider than the gaps
        // between them — so the bloom chain re-assembles the array as one white
        // disc and none of the figures are on screen. Only the pen tips, the
        // rune band and the core seed should glow; the rest stays crisp.
        if (stage == KillEffectInstance.STAGE_MAGIC_CIRCLE
                || stage == KillEffectInstance.STAGE_MAGIC_TOWER) {
            threshold = 0.85f;
            bloom    *= 0.7f;
            radius   *= 0.7f;
        }

        if (thaum != null) {
            float sigil  = thaum.sigilAlpha(nowMs);
            float surge  = thaum.sigilSurge(nowMs);
            float slam   = thaum.grandAlpha(nowMs) * thaum.grandDescent(nowMs);
            float sphere = thaum.sphereAlpha(nowMs);

            // Nothing but the neon strokes and the nova ball clear this
            // threshold, so the sky around the sigil stays clean.
            threshold = 0.72f;
            bloom  = (0.55f * sigil + 0.90f * surge + 1.7f * slam + 2.6f * sphere)
                    * mergeMult * thaumFade;
            // A halo, not a screen haze: the sigil supplies its own glow, and
            // this only blooms the strokes outwards.
            radius = 9f + 7f * thaum.sphereGrow(nowMs);
        }

        // ── Black hole screen-space / flash / shockwave params ────
        int   bhStage     = 0;
        float bhProgress  = 0f;  // BH: stage progress;  Nova: hypernova progress
        float bhIntensity = 0f;  // BH: merge mult;      Flash: bell pulse
        float bhShadowRadius = 0f; // BH: horizon radius in blocks

        if (stage >= KillEffectInstance.STAGE_BLACK_HOLE
                && stage <= KillEffectInstance.STAGE_COLLAPSE) {
            // ── Black hole phases: gravitational lensing ──────────
            // Entry ramp only on stage 3 (first BH stage): later BH stages
            // share the same strengths, so ramping them would dip.
            float entry = 1f;
            if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
                entry = gemini$smoothstep01(progress / 0.15f);
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
            // Same curve the world-space billboard is sized from — the screen
            // space shadow has to cover exactly that disk, not its own guess.
            bhShadowRadius = killEffect.getPrimaryBlackHoleShadowRadius(nowMs);

        } else if (stage == KillEffectInstance.STAGE_FLASH) {
            // ── Glow flash: multi-pulse light emission after void ──
            // The whole chain is a fraction of what it was. Bloom 3.2 with a
            // radius of 36 over a screen already carrying the detonation is not
            // a brighter blast, it is a white frame: the flash has to read as an
            // event, and you cannot see an event through fog.
            float entry = gemini$smoothstep01(progress / 0.10f);
            bloom    = Math.max(bloom, 1.7f * entry);
            chromatic = 0.55f * entry;
            godRay   = 0.85f * entry;
            radius   = 20f * entry;

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
            float entry = gemini$smoothstep01(progress / 0.05f);
            float pulse = 0.90f + 0.10f
                    * Math.abs((float)Math.sin(progress * Math.PI * 7.0f));
            bloom    = Math.max(bloom, (1.7f + (1.5f - 1.7f) * entry) * pulse);
            godRay   = 0.85f + (1.15f - 0.85f) * entry;
            chromatic = 0.55f + (0.42f - 0.55f) * entry;
            distort  = 0.88f * entry;
            radius   = 20f + (26f - 20f) * entry;

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

            bloom    = Math.max(killEffect.getBloomStrength() * mergeMult * 1.4f, 1.5f) * decay;
            distort  = 0.88f * decay;
            godRay   = 0.35f + 0.80f * decay;
            chromatic = 0.42f * decay;
            radius   = 26f * decay;

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

        // The residual-light machinery (screen-space lighting, volumetric
        // shafts, SSRT) belongs to the Hypernova detonation, so Thaumaturgy
        // skips the whole block rather than borrowing its look.
        if (center != null && thaum == null) {
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
                        ? gemini$smoothstep01(progress / 0.15f) : 1f;
                li  = 0.75f * entry;
                lr  = 24f;
                lcR = 1f; lcG = 0.55f; lcB = 0.15f;
            } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                // Energy builds as the hole collapses, then dies with it —
                // fade the light out over the last 30% so the transition
                // into the silent VOID stage has no light pop.
                float dieOut = 1f - gemini$smoothstep01((progress - 0.7f) / 0.3f);
                li  = (0.75f + progress * 1.05f) * dieOut;
                lr  = 24f + progress * 10f;
                lcR = 1f; lcG = 0.60f; lcB = 0.20f;
            } else if (stage == KillEffectInstance.STAGE_FLASH) {
                // Pulse with the flash bell curve, white-hot
                li  = 1.9f * bhIntensity;
                lr  = 30f;
                lcR = 1f; lcG = 0.97f; lcB = 0.90f;
            } else if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                // Blinding at detonation, slow ease-out decay
                float entry = gemini$smoothstep01(progress / 0.05f);
                float stellarPulse = 0.90f + 0.10f
                        * Math.abs((float)Math.sin(progress * Math.PI * 7.0f));
                li  = 2.3f * (1f - progress * 0.35f) * entry * stellarPulse;
                lr  = 40f;
                lcR = 1f; lcG = 0.95f; lcB = 0.85f;
            } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
                // Residual ember starts at the sustained hypernova's endpoint.
                // fade-out stage's entry (li 0.5, lr 26, color 1.0/0.60/0.28).
                float d1 = 1.0f - progress;
                float decay = d1 * d1;
                li  = 0.55f + 0.95f * decay;
                lr  = 28f + 12f * decay;
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

            if (stage < 0 && killEffect.isHellHandMode()) {
                // Hell's Hand mode: warm flickering torchlight from the
                // molten rift (depth-aware lighting; no god rays / SSR).
                // Kept tight (lr 12) so it reads as light spilling from
                // the gate, not a full-screen orange wash.
                float flick = 0.72f + 0.28f
                        * (float) Math.sin(nowMs * 0.014)
                        * (float) Math.sin(nowMs * 0.031);
                li  = 0.85f * flick * hellFade;
                lr  = 12f;
                lcR = 1f; lcG = 0.42f; lcB = 0.12f;
            }

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
                        ? 0.9f * (1f - gemini$smoothstep01((progress - 0.35f) / 0.50f))
                        : 0f;
                if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
                    volumetricSteps = 24;
                }
            }
        }

        // ── Chain fade: eases the whole post chain in AND out ──────
        // Ramps in over the first 0.9s (smoothstep); ramps back out over
        // the fade-out stage so the ACES tone-map/vignette never pops off
        // when the chain deactivates at the end of the effect. Thaumaturgy
        // keeps this at 0, which leaves the frame ungraded — the sigil is
        // meant to light up a night sky, not to recolour one.
        float chainFade = globalFadeIn * hellFade * hyperFade;
        if (stage == KillEffectInstance.STAGE_FADE_OUT) {
            float t = progress;
            chainFade *= 1f - t * t * (3f - 2f * t);
        }

        KillEffectPostProcessor.processFrame(
                bloom * globalFadeIn, threshold,
                distort * globalFadeIn, godRay * globalFadeIn,
                chromatic * globalFadeIn, radius * globalFadeIn,
                center, null,
                bhStage, bhProgress, bhIntensity,
                bhShadowRadius,
                lightWorldPos, lightColor,
                ssrIntensity, volumetricSteps,
                chainFade, bloomFalloff);

        }

        // ── Sweep Attack post-processing (distortion + chromatic) ──
        SweepingAttackVFX sweep = Gemini.moduleManager.getModule(SweepingAttackVFX.class);
        if (sweep != null && sweep.enabled && sweep.hasActiveEffects()) {
            sweep.processPost();
        }

        // ── BlackHolePet gravitational lensing ─────────────────────
        // Its own pass with its own identity: it bends the frame around the
        // pet's shadow and adds nothing else — no bloom, no grade, no vignette.
        BlackHolePet pet = Gemini.moduleManager.getModule(BlackHolePet.class);
        if (pet != null && pet.enabled) {
            pet.processLens();
        }
    }

    /**
     * smoothstep(0,1,x) — zero-slope ease at both ends. Used for stage-entry
     * ramps so post-processing passes never pop in at full strength.
     */
    @Unique
    private static float gemini$smoothstep01(float x) {
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
