package geminiclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.GeminiLoadingOverlay;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.KeyInputEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void callKey(long p_90894_, int p_90895_, KeyEvent p_446050_, CallbackInfo ci) {
        // 启动加载画面驻留期：任意按键确认进入主菜单，事件就地消费
        if (this.minecraft.gui.overlay() instanceof GeminiLoadingOverlay overlay
                && overlay.onUserInput(p_90895_ == InputConstants.PRESS)) {
            ci.cancel();
            return;
        }
        Gemini.eventManager.post(EventTypes.KEY_INPUT,
                new KeyInputEvent(p_446050_.key(), p_446050_.keycode(), p_446050_.modifiers(), p_90895_));
    }
}
