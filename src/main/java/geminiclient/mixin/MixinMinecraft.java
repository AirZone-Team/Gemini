package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.ShutdownEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "<init>",at = @At("TAIL"))
    private static void faithsRegister(GameConfig gameConfig, CallbackInfo ci) {
        Gemini.init();
    }

    @Inject(method = "close",at = @At("HEAD"))
    public void callshutdown(CallbackInfo ci) {
        Gemini.eventManager.call(new ShutdownEvent());
    }
}
