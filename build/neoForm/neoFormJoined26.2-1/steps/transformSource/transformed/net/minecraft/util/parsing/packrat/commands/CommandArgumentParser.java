package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface CommandArgumentParser<T> {
    T parseForCommands(StringReader reader) throws CommandSyntaxException;

    CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder);

    default <S> CommandArgumentParser<S> mapResult(Function<T, S> mapper) {
        return new CommandArgumentParser<S>() {
            @Override
            public S parseForCommands(StringReader reader) throws CommandSyntaxException {
                return mapper.apply((T)CommandArgumentParser.this.parseForCommands(reader));
            }

            @Override
            public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder) {
                return CommandArgumentParser.this.parseForSuggestions(suggestionsBuilder);
            }
        };
    }

    default <T, O> CommandArgumentParser<T> withCodec(
        DynamicOps<O> ops, CommandArgumentParser<O> valueParser, Codec<T> codec, DynamicCommandExceptionType exceptionType
    ) {
        return new CommandArgumentParser<T>() {
            @Override
            public T parseForCommands(StringReader reader) throws CommandSyntaxException {
                int cursor = reader.getCursor();
                O tag = valueParser.parseForCommands(reader);
                DataResult<T> result = codec.parse(ops, tag);
                return result.getOrThrow(message -> {
                    reader.setCursor(cursor);
                    return exceptionType.createWithContext(reader, message);
                });
            }

            @Override
            public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder) {
                return CommandArgumentParser.this.parseForSuggestions(suggestionsBuilder);
            }
        };
    }
}
