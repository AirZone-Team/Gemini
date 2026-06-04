package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.RotationAnimationEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.FallFlyingEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.JumpEvent;
import geminiclient.gemini.modules.impl.visual.FullLight;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static geminiclient.gemini.base.MinecraftInstance.mc;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float redirectGetYRotInJumpFromGround(LivingEntity instance, Operation<Float> original) {
        if (instance == mc.player) {
            JumpEvent event = new JumpEvent(instance.getYRot());
            Gemini.eventManager.call(event);
            return event.getYaw();
        }
        return original.call(instance);
    }

    @ModifyExpressionValue(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float modifyFallFlyingPitch(float original) {
        FallFlyingEvent event = new FallFlyingEvent(original);
        Gemini.eventManager.call(event);
        return event.getPitch();
    }

    @Redirect(
        method = "tickHeadTurn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
        )
    )
    private float modifyHeadYaw(LivingEntity entity) {
        if (entity == Minecraft.getInstance().player) {
            RotationAnimationEvent event = new RotationAnimationEvent(entity.getYRot(), 0.0F, 0.0F, 0.0F);
            Gemini.eventManager.call(event);
            return event.getYaw();
        }
        return entity.getYRot();
    }

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void hasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity thisEntity = (LivingEntity)(Object)this;
        if (thisEntity == Minecraft.getInstance().player) {
            FullLight FullLight = Gemini.moduleManager.getModule(FullLight.class);
            if (effect == MobEffects.NIGHT_VISION && FullLight.enabled) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}
