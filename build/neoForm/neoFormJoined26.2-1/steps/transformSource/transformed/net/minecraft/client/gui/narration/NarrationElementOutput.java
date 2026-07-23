package net.minecraft.client.gui.narration;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface NarrationElementOutput {
    default void add(NarratedElementType type, Component contents) {
        this.add(type, NarrationThunk.from(contents.getString()));
    }

    default void add(NarratedElementType type, String contents) {
        this.add(type, NarrationThunk.from(contents));
    }

    default void add(NarratedElementType type, Component... contents) {
        this.add(type, NarrationThunk.from(ImmutableList.copyOf(contents)));
    }

    void add(final NarratedElementType type, final NarrationThunk<?> contents);

    NarrationElementOutput nest();
}
