package geminiclient.mixin;

import geminiclient.gemini.base.MainMenuScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static geminiclient.gemini.base.MinecraftInstance.mc;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void redirectToMainMenu(CallbackInfo ci) {
        ci.cancel();
        mc.gui.setScreen(new MainMenuScreen());
    }
}
