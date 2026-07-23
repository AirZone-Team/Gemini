package net.minecraft.client.gui.screens.dialog.input;

import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.input.InputControl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface InputControlHandler<T extends InputControl> {
    void addControl(T input, Screen screen, InputControlHandler.Output output);

    @FunctionalInterface
    interface Output {
        void accept(LayoutElement element, Action.ValueGetter valueGetter);
    }
}
