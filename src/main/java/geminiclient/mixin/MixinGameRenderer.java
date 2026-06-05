package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.modules.impl.visual.FullLight;
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

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    public void inject2D(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (renderLevel) {
            Gemini.eventManager.call(new Render3DEvent(null, deltaTracker.getGameTimeDeltaPartialTick(false)));
        }
        int i = (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
        int j = (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(this.minecraft, this.gameRenderState.guiRenderState, i, j);
        Gemini.eventManager.call(new Render2DEvent(g, g.pose()));
    }

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionScale(LivingEntity pLivingEntity, float pNanoTime, CallbackInfoReturnable<Float> cir) {
        FullLight module = Gemini.moduleManager.getModule(FullLight.class);
        if (module.enabled) {
            cir.setReturnValue(1f);
            cir.cancel();
        }
    }
}
