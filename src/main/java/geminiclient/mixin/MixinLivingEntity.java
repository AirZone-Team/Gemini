package geminiclient.mixin;

import geminiclient.gemini.Gemini;
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

@Mixin(LivingEntity.class)
public class MixinLivingEntity {
    @Redirect(method = "jumpFromGround",at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F",opcode = 182,ordinal = 0))
    public float onJump(LivingEntity instance) {
        JumpEvent event = new JumpEvent(instance.getYRot());
        Gemini.eventManager.call(event);
        return event.getYaw();
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
