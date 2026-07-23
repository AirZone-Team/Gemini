package net.minecraft.client.gui.components;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record WidgetSprites(Identifier enabled, Identifier disabled, Identifier enabledFocused, Identifier disabledFocused) {
    public WidgetSprites(Identifier sprite) {
        this(sprite, sprite, sprite, sprite);
    }

    public WidgetSprites(Identifier sprite, Identifier focused) {
        this(sprite, sprite, focused, focused);
    }

    public WidgetSprites(Identifier enabled, Identifier disabled, Identifier focused) {
        this(enabled, disabled, focused, disabled);
    }

    public Identifier get(boolean enabled, boolean focused) {
        if (enabled) {
            return focused ? this.enabledFocused : this.enabled;
        } else {
            return focused ? this.disabledFocused : this.disabled;
        }
    }
}
