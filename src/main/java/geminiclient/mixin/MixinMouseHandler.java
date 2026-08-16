package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.MouseButtonInputEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Inject(method = "onButton", at = @At("HEAD"))
    public void callButton(long p_91527_, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        Gemini.eventManager.post(EventTypes.MOUSE_BUTTON_INPUT,
                new MouseButtonInputEvent(rawButtonInfo.button(), rawButtonInfo.modifiers(), action));
    }
}
