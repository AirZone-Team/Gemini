package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class MixinPlayer {
    @ModifyExpressionValue(method = {"causeExtraKnockback", "doSweepAttack*"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float modifyAttackYaw(float original) {
        AttackYawEvent event = new AttackYawEvent(original);
        Gemini.eventManager.call(event);
        return event.getYaw();
    }
}
