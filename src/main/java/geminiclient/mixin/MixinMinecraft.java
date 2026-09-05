package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.GeminiLoadingOverlay;
import geminiclient.mixin.access.AccessLoadingOverlay;
import geminiclient.gemini.event.EventTypes;
import geminiclient.gemini.event.events.impl.ShutdownEvent;
import geminiclient.gemini.modules.impl.visual.ESP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.main.GameConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "<init>",at = @At("TAIL"))
    private static void faithsRegister(GameConfig gameConfig, CallbackInfo ci) {
        Gemini.init();
    }

    /**
     * 用客户端自定义加载画面替换初始资源重载的原版 LoadingOverlay。
     * reload / onFinish / fadeIn 原样转交，资源流转（失败回滚、成功进主菜单）
     * 不受影响；其余 setOverlay 调用（构造器内没有，但保底透传）原样放行。
     */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setOverlay(Lnet/minecraft/client/gui/screens/Overlay;)V"))
    private static void gemini$customLoadingOverlay(Gui instance, Overlay overlay, Operation<Void> original) {
        if (overlay instanceof LoadingOverlay loading) {
            AccessLoadingOverlay access = (AccessLoadingOverlay) (Object) loading;
            original.call(instance, new GeminiLoadingOverlay(
                    access.minecraft(), access.reload(), access.onFinish(), access.fadeIn()));
        } else {
            original.call(instance, overlay);
        }
    }

    @Inject(method = "close",at = @At("HEAD"))
    public void callshutdown(CallbackInfo ci) {
        Gemini.eventManager.post(EventTypes.SHUTDOWN, new ShutdownEvent());
    }

    @Inject(method = "shouldEntityAppearGlowing",at = @At("RETURN"), cancellable = true)
    public void Glowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Gemini.moduleManager.getModule(ESP.class).shouldGlow(entity)) {
            cir.setReturnValue(true);
        }
    }
}
