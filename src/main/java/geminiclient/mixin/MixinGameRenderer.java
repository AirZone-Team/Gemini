package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.impl.visual.FullLight;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
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

    @Shadow @Final private GuiRenderState guiRenderState;

    @Inject(method = "render",at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    public void inject2D(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        GuiGraphics g = new GuiGraphics(this.minecraft,this.guiRenderState);
        Gemini.eventManager.call(new Render2DEvent(g,g.pose()));
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
