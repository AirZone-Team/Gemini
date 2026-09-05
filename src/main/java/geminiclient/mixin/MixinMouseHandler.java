package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.GeminiLoadingOverlay;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.MouseButtonInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    public void callButton(long p_91527_, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        // 启动加载画面驻留期：鼠标按下确认进入主菜单，事件就地消费
        if (this.minecraft.gui.overlay() instanceof GeminiLoadingOverlay overlay
                && overlay.onUserInput(action == GLFW.GLFW_PRESS)) {
            ci.cancel();
            return;
        }
        Gemini.eventManager.post(EventTypes.MOUSE_BUTTON_INPUT,
                new MouseButtonInputEvent(rawButtonInfo.button(), rawButtonInfo.modifiers(), action));
    }
}
