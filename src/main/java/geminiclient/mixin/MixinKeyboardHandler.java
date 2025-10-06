package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Inject(method = "keyPress",at = @At("HEAD"))
    public void callKey(long p_90894_, int p_90895_, KeyEvent p_446050_, CallbackInfo ci) {
        Gemini.eventManager.call(new geminiclient.gemini.events.impl.KeyEvent(p_446050_.key(),p_446050_.scancode(), p_446050_.modifiers()));
    }
}
