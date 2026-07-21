package geminiclient.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import geminiclient.gemini.modules.impl.visual.clickgui.AbstractClickGuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Stack;

@Mixin(Gui.class)
public class MixinGui {

    /**
     * Defer ClickGui extraction until after the client HUD has been submitted,
     * so it renders above every HUD element.
     */
    @WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;extractScreen(Lnet/minecraft/client/gui/screens/Screen;Ljava/util/Stack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private boolean deferClickGuiExtraction(Screen screen, Stack<Screen> screenLayers,
                                             GuiGraphicsExtractor graphics, int mouseX,
                                             int mouseY, float deltaTicks) {
        return !(screen instanceof AbstractClickGuiScreen);
    }
}
