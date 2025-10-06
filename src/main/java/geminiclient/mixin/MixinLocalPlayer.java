package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.events.impl.UpdateEvent;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayer {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",shift = At.Shift.BEFORE))
    private static void registerUpdateEvent(CallbackInfo ci) {
        Gemini.eventManager.call(new UpdateEvent());
    }
}
