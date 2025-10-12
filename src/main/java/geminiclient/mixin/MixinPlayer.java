package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.moveFixEvent.AttackYawEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class MixinPlayer {
    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private float hookFixRotation(Player instance) {
        AttackYawEvent event = new AttackYawEvent(instance.getYRot());
        Gemini.eventManager.call(event);
        return event.getYaw();
    }
}