package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.RotationAnimationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static geminiclient.gemini.base.MinecraftInstance.mc;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At("HEAD")
    )
    private void onExtractRenderState(T entity, S state, float partialTicks, CallbackInfo ci) {
        RotationAnimationEvent.currentEntity = entity;
    }

    @Redirect(
        method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"
        )
    )
    private float redirectHeadYaw(float pDelta, float pStart, float pEnd) {
        RotationAnimationEvent event = new RotationAnimationEvent(pEnd, pStart, 0.0F, 0.0F);
        if (RotationAnimationEvent.currentEntity == Minecraft.getInstance().player) {
            Gemini.eventManager.post(EventTypes.ROTATION_ANIMATION, event);
        }
        return Mth.rotLerp(pDelta, event.getLastYaw(), event.getYaw());
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float modifyPitch(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = new RotationAnimationEvent(0.0f, 0.0f, entity.getXRot(), entity.getXRot(0.0f));
            Gemini.eventManager.post(EventTypes.ROTATION_ANIMATION, event);
            return Mth.rotLerp(partialTicks, event.getLastPitch(), event.getPitch());
        }
        return original;
    }
}
