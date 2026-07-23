package net.minecraft.client.input;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MouseButtonInfo(@MouseButtonInfo.MouseButton int button, @InputWithModifiers.Modifiers int modifiers) implements InputWithModifiers {
    @Override
    public @MouseButtonInfo.MouseButton int input() {
        return this.button;
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    @org.intellij.lang.annotations.MagicConstant(intValues = {
        com.mojang.blaze3d.platform.InputConstants.PRESS, 
        com.mojang.blaze3d.platform.InputConstants.RELEASE
    })
    public @interface Action {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    @org.intellij.lang.annotations.MagicConstant(intValues = {
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE,
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_4,
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_5, 
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_6,
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_7, 
        com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_8,
    })
    public @interface MouseButton {
    }
}
