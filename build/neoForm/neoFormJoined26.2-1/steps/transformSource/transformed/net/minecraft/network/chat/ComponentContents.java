package net.minecraft.network.chat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.MapCodec;
import java.util.Optional;

public interface ComponentContents {
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style currentStyle) {
        return Optional.empty();
    }

    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        return Optional.empty();
    }

    default MutableComponent resolve(ResolutionContext context, int recursionDepth) throws CommandSyntaxException {
        return MutableComponent.create(this);
    }

    MapCodec<? extends ComponentContents> codec();
}
