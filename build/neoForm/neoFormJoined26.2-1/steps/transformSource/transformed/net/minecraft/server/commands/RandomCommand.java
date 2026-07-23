package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import org.jspecify.annotations.Nullable;

public class RandomCommand {
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_large")
    );
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_small")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("random")
                .then(drawRandomValueTree("value", false))
                .then(drawRandomValueTree("roll", true))
                .then(
                    Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(
                            Commands.literal("*")
                                .executes(c -> resetAllSequences(c.getSource()))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(c -> resetAllSequencesAndSetNewDefaults(c.getSource(), IntegerArgumentType.getInteger(c, "seed"), true, true))
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    c -> resetAllSequencesAndSetNewDefaults(
                                                        c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "seed"),
                                                        BoolArgumentType.getBool(c, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            c -> resetAllSequencesAndSetNewDefaults(
                                                                c.getSource(),
                                                                IntegerArgumentType.getInteger(c, "seed"),
                                                                BoolArgumentType.getBool(c, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(c, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("sequence", IdentifierArgument.id())
                                .suggests(RandomCommand::suggestRandomSequence)
                                .executes(c -> resetSequence(c.getSource(), IdentifierArgument.getId(c, "sequence")))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            c -> resetSequence(
                                                c.getSource(), IdentifierArgument.getId(c, "sequence"), IntegerArgumentType.getInteger(c, "seed"), true, true
                                            )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    c -> resetSequence(
                                                        c.getSource(),
                                                        IdentifierArgument.getId(c, "sequence"),
                                                        IntegerArgumentType.getInteger(c, "seed"),
                                                        BoolArgumentType.getBool(c, "includeWorldSeed"),
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            c -> resetSequence(
                                                                c.getSource(),
                                                                IdentifierArgument.getId(c, "sequence"),
                                                                IntegerArgumentType.getInteger(c, "seed"),
                                                                BoolArgumentType.getBool(c, "includeWorldSeed"),
                                                                BoolArgumentType.getBool(c, "includeSequenceId")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String name, boolean announce) {
        return Commands.literal(name)
            .then(
                Commands.argument("range", RangeArgument.intRange())
                    .executes(c -> randomSample(c.getSource(), RangeArgument.Ints.getRange(c, "range"), null, announce))
                    .then(
                        Commands.argument("sequence", IdentifierArgument.id())
                            .suggests(RandomCommand::suggestRandomSequence)
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(
                                c -> randomSample(c.getSource(), RangeArgument.Ints.getRange(c, "range"), IdentifierArgument.getId(c, "sequence"), announce)
                            )
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> result = Lists.newArrayList();
        context.getSource().getServer().getRandomSequences().forAllSequences((key, sequence) -> result.add(key.toString()));
        return SharedSuggestionProvider.suggest(result, builder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable Identifier sequence, boolean announce) throws CommandSyntaxException {
        RandomSource random;
        if (sequence != null) {
            random = source.getServer().getRandomSequence(sequence);
        } else {
            random = source.getLevel().getRandom();
        }

        int min = range.min().orElse(Integer.MIN_VALUE);
        int max = range.max().orElse(Integer.MAX_VALUE);
        long span = (long)max - min;
        if (span == 0L) {
            throw ERROR_RANGE_TOO_SMALL.create();
        }

        if (span >= 2147483647L) {
            throw ERROR_RANGE_TOO_LARGE.create();
        }

        int value = Mth.randomBetweenInclusive(random, min, max);
        if (announce) {
            source.getServer()
                .getPlayerList()
                .broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), value, min, max), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.random.sample.success", value), false);
        }

        return value;
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        source.getServer().getRandomSequences().reset(sequence, level.getSeed());
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, Identifier sequence, int salt, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        source.getServer().getRandomSequences().reset(sequence, level.getSeed(), salt, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int count = source.getServer().getRandomSequences().clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", count), false);
        return count;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomSequences = source.getServer().getRandomSequences();
        randomSequences.setSeedDefaults(salt, includeWorldSeed, includeSequenceId);
        int count = randomSequences.clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", count), false);
        return count;
    }
}
