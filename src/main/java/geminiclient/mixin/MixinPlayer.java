package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.AttackSlowDownEvent;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class MixinPlayer {
    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack*"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = new AttackYawEvent(original);
        Gemini.eventManager.call(event);
        return event.getYaw();
    }

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"), cancellable = true)
    private void onCauseExtraKnockback(Entity entity, float knockbackAmount, Vec3 oldMovement, CallbackInfo ci) {
        AttackSlowDownEvent event = new AttackSlowDownEvent(entity, knockbackAmount);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
