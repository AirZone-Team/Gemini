package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.HeightmapTypeArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.SlotsArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.SwizzleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.Stopwatch;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.Attackable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.Targeting;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.inventory.SlotRange;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ExecuteCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TEST_AREA = 32768;
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (max, count) -> Component.translatableEscape("commands.execute.blocks.toobig", max, count)
    );
    private static final SimpleCommandExceptionType ERROR_CONDITIONAL_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.execute.conditional.fail")
    );
    private static final DynamicCommandExceptionType ERROR_CONDITIONAL_FAILED_COUNT = new DynamicCommandExceptionType(
        count -> Component.translatableEscape("commands.execute.conditional.fail_count", count)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (id, reason) -> Component.translatableEscape("commands.execute.function.instantiationFailure", id, reason)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralCommandNode<CommandSourceStack> execute = dispatcher.register(
            Commands.literal("execute").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        );
        dispatcher.register(
            Commands.literal("execute")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("run").redirect(dispatcher.getRoot()))
                .then(addConditionals(execute, Commands.literal("if"), true, context))
                .then(addConditionals(execute, Commands.literal("unless"), false, context))
                .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(execute, c -> {
                    List<CommandSourceStack> result = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(c, "targets")) {
                        result.add(c.getSource().withEntity(entity));
                    }

                    return result;
                })))
                .then(Commands.literal("at").then(Commands.argument("targets", EntityArgument.entities()).fork(execute, c -> {
                    List<CommandSourceStack> result = Lists.newArrayList();

                    for (Entity entity : EntityArgument.getOptionalEntities(c, "targets")) {
                        result.add(
                            c.getSource().withLevel((ServerLevel)entity.level()).withPosition(entity.position()).withRotation(entity.getRotationVector())
                        );
                    }

                    return result;
                })))
                .then(
                    Commands.literal("store")
                        .then(wrapStores(execute, Commands.literal("result"), true))
                        .then(wrapStores(execute, Commands.literal("success"), false))
                )
                .then(
                    Commands.literal("positioned")
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .redirect(execute, c -> c.getSource().withPosition(Vec3Argument.getVec3(c, "pos")).withAnchor(EntityAnchorArgument.Anchor.FEET))
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(execute, c -> {
                            List<CommandSourceStack> result = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(c, "targets")) {
                                result.add(c.getSource().withPosition(entity.position()));
                            }

                            return result;
                        })))
                        .then(Commands.literal("over").then(Commands.argument("heightmap", HeightmapTypeArgument.heightmap()).redirect(execute, c -> {
                            Vec3 position = c.getSource().getPosition();
                            ServerLevel level = c.getSource().getLevel();
                            double x = position.x();
                            double z = position.z();
                            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                                throw BlockPosArgument.ERROR_NOT_LOADED.create();
                            }

                            int height = level.getHeight(HeightmapTypeArgument.getHeightmap(c, "heightmap"), Mth.floor(x), Mth.floor(z));
                            return c.getSource().withPosition(new Vec3(x, height, z));
                        })))
                )
                .then(
                    Commands.literal("rotated")
                        .then(
                            Commands.argument("rot", RotationArgument.rotation())
                                .redirect(execute, c -> c.getSource().withRotation(RotationArgument.getRotation(c, "rot").getRotation(c.getSource())))
                        )
                        .then(Commands.literal("as").then(Commands.argument("targets", EntityArgument.entities()).fork(execute, c -> {
                            List<CommandSourceStack> result = Lists.newArrayList();

                            for (Entity entity : EntityArgument.getOptionalEntities(c, "targets")) {
                                result.add(c.getSource().withRotation(entity.getRotationVector()));
                            }

                            return result;
                        })))
                )
                .then(
                    Commands.literal("facing")
                        .then(
                            Commands.literal("entity")
                                .then(
                                    Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("anchor", EntityAnchorArgument.anchor()).fork(execute, c -> {
                                            List<CommandSourceStack> result = Lists.newArrayList();
                                            EntityAnchorArgument.Anchor anchor = EntityAnchorArgument.getAnchor(c, "anchor");

                                            for (Entity entity : EntityArgument.getOptionalEntities(c, "targets")) {
                                                result.add(c.getSource().facing(entity, anchor));
                                            }

                                            return result;
                                        }))
                                )
                        )
                        .then(Commands.argument("pos", Vec3Argument.vec3()).redirect(execute, c -> c.getSource().facing(Vec3Argument.getVec3(c, "pos"))))
                )
                .then(
                    Commands.literal("align")
                        .then(
                            Commands.argument("axes", SwizzleArgument.swizzle())
                                .redirect(execute, c -> c.getSource().withPosition(c.getSource().getPosition().align(SwizzleArgument.getSwizzle(c, "axes"))))
                        )
                )
                .then(
                    Commands.literal("anchored")
                        .then(
                            Commands.argument("anchor", EntityAnchorArgument.anchor())
                                .redirect(execute, c -> c.getSource().withAnchor(EntityAnchorArgument.getAnchor(c, "anchor")))
                        )
                )
                .then(
                    Commands.literal("in")
                        .then(
                            Commands.argument("dimension", DimensionArgument.dimension())
                                .redirect(execute, c -> c.getSource().withLevel(DimensionArgument.getDimension(c, "dimension")))
                        )
                )
                .then(
                    Commands.literal("summon")
                        .then(
                            Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
                                .redirect(execute, c -> spawnEntityAndRedirect(c.getSource(), ResourceArgument.getSummonableEntityType(c, "entity")))
                        )
                )
                .then(createRelationOperations(execute, Commands.literal("on")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wrapStores(
        LiteralCommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> literal, boolean storeResult
    ) {
        literal.then(
            Commands.literal("score")
                .then(
                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                        .then(
                            Commands.argument("objective", ObjectiveArgument.objective())
                                .redirect(
                                    execute,
                                    c -> storeValue(
                                        c.getSource(),
                                        ScoreHolderArgument.getNamesWithDefaultWildcard(c, "targets"),
                                        ObjectiveArgument.getObjective(c, "objective"),
                                        storeResult
                                    )
                                )
                        )
                )
        );
        literal.then(
            Commands.literal("bossbar")
                .then(
                    Commands.argument("id", IdentifierArgument.id())
                        .suggests(BossBarCommands.SUGGEST_BOSS_BAR)
                        .then(Commands.literal("value").redirect(execute, c -> storeValue(c.getSource(), BossBarCommands.getBossBar(c), true, storeResult)))
                        .then(Commands.literal("max").redirect(execute, c -> storeValue(c.getSource(), BossBarCommands.getBossBar(c), false, storeResult)))
                )
        );

        for (DataCommands.DataProvider provider : DataCommands.TARGET_PROVIDERS) {
            provider.wrap(
                literal,
                p -> p.then(
                    Commands.argument("path", NbtPathArgument.nbtPath())
                        .then(
                            Commands.literal("int")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> IntTag.valueOf((int)(v * DoubleArgumentType.getDouble(c, "scale"))),
                                                storeResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("float")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> FloatTag.valueOf((float)(v * DoubleArgumentType.getDouble(c, "scale"))),
                                                storeResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("short")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> ShortTag.valueOf((short)(v * DoubleArgumentType.getDouble(c, "scale"))),
                                                storeResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("long")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> LongTag.valueOf((long)(v * DoubleArgumentType.getDouble(c, "scale"))),
                                                storeResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("double")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> DoubleTag.valueOf(v * DoubleArgumentType.getDouble(c, "scale")),
                                                storeResult
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("byte")
                                .then(
                                    Commands.argument("scale", DoubleArgumentType.doubleArg())
                                        .redirect(
                                            execute,
                                            c -> storeData(
                                                c.getSource(),
                                                provider.access(c),
                                                NbtPathArgument.getPath(c, "path"),
                                                v -> ByteTag.valueOf((byte)(v * DoubleArgumentType.getDouble(c, "scale"))),
                                                storeResult
                                            )
                                        )
                                )
                        )
                )
            );
        }

        return literal;
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, Collection<ScoreHolder> names, Objective objective, boolean storeResult) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        return source.withCallback((success, result) -> {
            for (ScoreHolder name : names) {
                ScoreAccess score = scoreboard.getOrCreatePlayerScore(name, objective);
                int value = storeResult ? result : (success ? 1 : 0);
                score.set(value);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeValue(CommandSourceStack source, CustomBossEvent event, boolean storeIntoValue, boolean storeResult) {
        return source.withCallback((success, result) -> {
            int value = storeResult ? result : (success ? 1 : 0);
            if (storeIntoValue) {
                event.setValue(value);
            } else {
                event.setMax(value);
            }
        }, CommandResultCallback::chain);
    }

    private static CommandSourceStack storeData(
        CommandSourceStack source, DataAccessor accessor, NbtPathArgument.NbtPath path, IntFunction<Tag> constructor, boolean storeResult
    ) {
        return source.withCallback((success, result) -> {
            try {
                CompoundTag data = accessor.getData();
                int value = storeResult ? result : (success ? 1 : 0);
                path.set(data, constructor.apply(value));
                accessor.setData(data);
            } catch (CommandSyntaxException var8) {
            }
        }, CommandResultCallback::chain);
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
        return chunk == null ? false : chunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && level.areEntitiesLoaded(chunkPos.pack());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditionals(
        CommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> parent, boolean expected, CommandBuildContext context
    ) {
        parent.then(
                Commands.literal("block")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    execute,
                                    Commands.argument("block", BlockPredicateArgument.blockPredicate(context)),
                                    expected,
                                    c -> BlockPredicateArgument.getBlockPredicate(c, "block")
                                        .test(new BlockInWorld(c.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(c, "pos"), true))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("biome")
                    .then(
                        Commands.argument("pos", BlockPosArgument.blockPos())
                            .then(
                                addConditional(
                                    execute,
                                    Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME)),
                                    expected,
                                    c -> ResourceOrTagArgument.getResourceOrTag(c, "biome", Registries.BIOME)
                                        .test(c.getSource().getLevel().getBiome(BlockPosArgument.getLoadedBlockPos(c, "pos")))
                                )
                            )
                    )
            )
            .then(
                Commands.literal("loaded")
                    .then(
                        addConditional(
                            execute,
                            Commands.argument("pos", BlockPosArgument.blockPos()),
                            expected,
                            c -> isChunkLoaded(c.getSource().getLevel(), BlockPosArgument.getBlockPos(c, "pos"))
                        )
                    )
            )
            .then(
                Commands.literal("dimension")
                    .then(
                        addConditional(
                            execute,
                            Commands.argument("dimension", DimensionArgument.dimension()),
                            expected,
                            c -> DimensionArgument.getDimension(c, "dimension") == c.getSource().getLevel()
                        )
                    )
            )
            .then(
                Commands.literal("score")
                    .then(
                        Commands.argument("target", ScoreHolderArgument.scoreHolder())
                            .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                            .then(
                                Commands.argument("targetObjective", ObjectiveArgument.objective())
                                    .then(
                                        Commands.literal("=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            execute,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            expected,
                                                            c -> checkScore(c, (a, b) -> a == b)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            execute,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            expected,
                                                            c -> checkScore(c, (a, b) -> a < b)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("<=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            execute,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            expected,
                                                            c -> checkScore(c, (a, b) -> a <= b)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            execute,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            expected,
                                                            c -> checkScore(c, (a, b) -> a > b)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal(">=")
                                            .then(
                                                Commands.argument("source", ScoreHolderArgument.scoreHolder())
                                                    .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                    .then(
                                                        addConditional(
                                                            execute,
                                                            Commands.argument("sourceObjective", ObjectiveArgument.objective()),
                                                            expected,
                                                            c -> checkScore(c, (a, b) -> a >= b)
                                                        )
                                                    )
                                            )
                                    )
                                    .then(
                                        Commands.literal("matches")
                                            .then(
                                                addConditional(
                                                    execute,
                                                    Commands.argument("range", RangeArgument.intRange()),
                                                    expected,
                                                    c -> checkScore(c, RangeArgument.Ints.getRange(c, "range"))
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("blocks")
                    .then(
                        Commands.argument("start", BlockPosArgument.blockPos())
                            .then(
                                Commands.argument("end", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("destination", BlockPosArgument.blockPos())
                                            .then(addIfBlocksConditional(execute, Commands.literal("all"), expected, false))
                                            .then(addIfBlocksConditional(execute, Commands.literal("masked"), expected, true))
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("entity")
                    .then(
                        Commands.argument("entities", EntityArgument.entities())
                            .fork(execute, c -> expect(c, expected, !EntityArgument.getOptionalEntities(c, "entities").isEmpty()))
                            .executes(createNumericConditionalHandler(expected, c -> EntityArgument.getOptionalEntities(c, "entities").size()))
                    )
            )
            .then(
                Commands.literal("predicate")
                    .then(
                        addConditional(
                            execute,
                            Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(context)),
                            expected,
                            c -> checkCustomPredicate(c.getSource(), ResourceOrIdArgument.getLootPredicate(c, "predicate"))
                        )
                    )
            )
            .then(
                Commands.literal("function")
                    .then(
                        Commands.argument("name", FunctionArgument.functions())
                            .suggests(FunctionCommand.SUGGEST_FUNCTION)
                            .fork(execute, new ExecuteCommand.ExecuteIfFunctionCustomModifier(expected))
                    )
            )
            .then(
                Commands.literal("items")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context))
                                                    .fork(
                                                        execute,
                                                        c -> expect(
                                                            c,
                                                            expected,
                                                            countItems(
                                                                    EntityArgument.getEntities(c, "entities"),
                                                                    SlotsArgument.getSlots(c, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(c, "item_predicate")
                                                                )
                                                                > 0
                                                        )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            expected,
                                                            c -> countItems(
                                                                EntityArgument.getEntities(c, "entities"),
                                                                SlotsArgument.getSlots(c, "slots"),
                                                                ItemPredicateArgument.getItemPredicate(c, "item_predicate")
                                                            )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .then(
                                        Commands.argument("slots", SlotsArgument.slots())
                                            .then(
                                                Commands.argument("item_predicate", ItemPredicateArgument.itemPredicate(context))
                                                    .fork(
                                                        execute,
                                                        c -> expect(
                                                            c,
                                                            expected,
                                                            countItems(
                                                                    c.getSource(),
                                                                    BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                                    SlotsArgument.getSlots(c, "slots"),
                                                                    ItemPredicateArgument.getItemPredicate(c, "item_predicate")
                                                                )
                                                                > 0
                                                        )
                                                    )
                                                    .executes(
                                                        createNumericConditionalHandler(
                                                            expected,
                                                            c -> countItems(
                                                                c.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                                SlotsArgument.getSlots(c, "slots"),
                                                                ItemPredicateArgument.getItemPredicate(c, "item_predicate")
                                                            )
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("stopwatch")
                    .then(
                        Commands.argument("id", IdentifierArgument.id())
                            .suggests(StopwatchCommand.SUGGEST_STOPWATCHES)
                            .then(
                                addConditional(
                                    execute,
                                    Commands.argument("range", RangeArgument.floatRange()),
                                    expected,
                                    c -> checkStopwatch(c, RangeArgument.Floats.getRange(c, "range"))
                                )
                            )
                    )
            );

        for (DataCommands.DataProvider provider : DataCommands.SOURCE_PROVIDERS) {
            parent.then(
                provider.wrap(
                    Commands.literal("data"),
                    p -> p.then(
                        Commands.argument("path", NbtPathArgument.nbtPath())
                            .fork(execute, c -> expect(c, expected, checkMatchingData(provider.access(c), NbtPathArgument.getPath(c, "path")) > 0))
                            .executes(createNumericConditionalHandler(expected, c -> checkMatchingData(provider.access(c), NbtPathArgument.getPath(c, "path"))))
                    )
                )
            );
        }

        return parent;
    }

    private static int countItems(Iterable<? extends SlotProvider> sources, SlotRange slotRange, Predicate<ItemStack> predicate) {
        int count = 0;

        for (SlotProvider slotProvider : sources) {
            IntList slots = slotRange.slots();

            for (int i = 0; i < slots.size(); i++) {
                int slotId = slots.getInt(i);
                SlotAccess slot = slotProvider.getSlot(slotId);
                if (slot != null) {
                    ItemStack contents = slot.get();
                    if (predicate.test(contents)) {
                        count += contents.getCount();
                    }
                }
            }
        }

        return count;
    }

    private static int countItems(CommandSourceStack source, BlockPos pos, SlotRange slotRange, Predicate<ItemStack> predicate) throws CommandSyntaxException {
        int count = 0;
        Container container = ItemCommands.getContainer(source, pos, ItemCommands.ERROR_SOURCE_NOT_A_CONTAINER);
        int containerSize = container.getContainerSize();
        IntList slots = slotRange.slots();

        for (int i = 0; i < slots.size(); i++) {
            int slotId = slots.getInt(i);
            if (slotId >= 0 && slotId < containerSize) {
                ItemStack contents = container.getItem(slotId);
                if (predicate.test(contents)) {
                    count += contents.getCount();
                }
            }
        }

        return count;
    }

    private static Command<CommandSourceStack> createNumericConditionalHandler(boolean expected, ExecuteCommand.CommandNumericPredicate condition) {
        return expected ? c -> {
            int count = condition.test(c);
            if (count > 0) {
                c.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", count), false);
                return count;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        } : c -> {
            int count = condition.test(c);
            if (count == 0) {
                c.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED_COUNT.create(count);
            }
        };
    }

    private static int checkMatchingData(DataAccessor accessor, NbtPathArgument.NbtPath path) throws CommandSyntaxException {
        return path.countMatching(accessor.getData());
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, ExecuteCommand.IntBiPredicate operation) throws CommandSyntaxException {
        ScoreHolder target = ScoreHolderArgument.getName(context, "target");
        Objective targetObjective = ObjectiveArgument.getObjective(context, "targetObjective");
        ScoreHolder source = ScoreHolderArgument.getName(context, "source");
        Objective sourceObjective = ObjectiveArgument.getObjective(context, "sourceObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo a = scoreboard.getPlayerScoreInfo(target, targetObjective);
        ReadOnlyScoreInfo b = scoreboard.getPlayerScoreInfo(source, sourceObjective);
        return a != null && b != null ? operation.test(a.value(), b.value()) : false;
    }

    private static boolean checkScore(CommandContext<CommandSourceStack> context, MinMaxBounds.Ints range) throws CommandSyntaxException {
        ScoreHolder target = ScoreHolderArgument.getName(context, "target");
        Objective targetObjective = ObjectiveArgument.getObjective(context, "targetObjective");
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(target, targetObjective);
        return scoreInfo == null ? false : range.matches(scoreInfo.value());
    }

    private static boolean checkStopwatch(CommandContext<CommandSourceStack> context, MinMaxBounds.Doubles range) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "id");
        Stopwatches stopwatches = context.getSource().getServer().getStopwatches();
        Stopwatch stopwatch = stopwatches.get(id);
        if (stopwatch == null) {
            throw StopwatchCommand.ERROR_DOES_NOT_EXIST.create(id);
        }

        long currentTime = Stopwatches.currentTime();
        double elapsedSeconds = stopwatch.elapsedSeconds(currentTime);
        return range.matches(elapsedSeconds);
    }

    private static boolean checkCustomPredicate(CommandSourceStack source, Holder<LootItemCondition> predicate) {
        ServerLevel level = source.getLevel();
        LootParams lootParams = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, source.getPosition())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
            .create(LootContextParamSets.COMMAND);
        LootContext context = new LootContext.Builder(lootParams).create(Optional.empty());
        context.pushVisitedElement(LootContext.createVisitedEntry(predicate.value()));
        return predicate.value().test(context);
    }

    private static Collection<CommandSourceStack> expect(CommandContext<CommandSourceStack> context, boolean expected, boolean result) {
        return result == expected ? Collections.singleton(context.getSource()) : Collections.emptyList();
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addConditional(
        CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> argument, boolean expected, ExecuteCommand.CommandPredicate predicate
    ) {
        return argument.fork(root, c -> expect(c, expected, predicate.test(c))).executes(c -> {
            if (expected == predicate.test(c)) {
                c.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
                return 1;
            } else {
                throw ERROR_CONDITIONAL_FAILED.create();
            }
        });
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addIfBlocksConditional(
        CommandNode<CommandSourceStack> root, ArgumentBuilder<CommandSourceStack, ?> argument, boolean expected, boolean skipAir
    ) {
        return argument.fork(root, c -> expect(c, expected, checkRegions(c, skipAir).isPresent()))
            .executes(expected ? c -> checkIfRegions(c, skipAir) : c -> checkUnlessRegions(c, skipAir));
    }

    private static int checkIfRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        OptionalInt count = checkRegions(context, skipAir);
        if (count.isPresent()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass_count", count.getAsInt()), false);
            return count.getAsInt();
        } else {
            throw ERROR_CONDITIONAL_FAILED.create();
        }
    }

    private static int checkUnlessRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        OptionalInt count = checkRegions(context, skipAir);
        if (count.isPresent()) {
            throw ERROR_CONDITIONAL_FAILED_COUNT.create(count.getAsInt());
        }

        context.getSource().sendSuccess(() -> Component.translatable("commands.execute.conditional.pass"), false);
        return 1;
    }

    private static OptionalInt checkRegions(CommandContext<CommandSourceStack> context, boolean skipAir) throws CommandSyntaxException {
        return checkRegions(
            context.getSource().getLevel(),
            BlockPosArgument.getLoadedBlockPos(context, "start"),
            BlockPosArgument.getLoadedBlockPos(context, "end"),
            BlockPosArgument.getLoadedBlockPos(context, "destination"),
            skipAir
        );
    }

    private static OptionalInt checkRegions(ServerLevel level, BlockPos startPos, BlockPos endPos, BlockPos destPos, boolean skipAir) throws CommandSyntaxException {
        BoundingBox from = BoundingBox.fromCorners(startPos, endPos);
        BoundingBox destination = BoundingBox.fromCorners(destPos, destPos.offset(from.getLength()));
        BlockPos offset = new BlockPos(destination.minX() - from.minX(), destination.minY() - from.minY(), destination.minZ() - from.minZ());
        long area = (long)from.getXSpan() * from.getYSpan() * from.getZSpan();
        if (area > 32768L) {
            throw ERROR_AREA_TOO_LARGE.create(32768, area);
        }

        int count = 0;
        RegistryAccess registryAccess = level.registryAccess();

        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            for (int z = from.minZ(); z <= from.maxZ(); z++) {
                for (int y = from.minY(); y <= from.maxY(); y++) {
                    for (int x = from.minX(); x <= from.maxX(); x++) {
                        BlockPos sourcePos = new BlockPos(x, y, z);
                        BlockPos destinationPos = sourcePos.offset(offset);
                        BlockState sourceBlock = level.getBlockState(sourcePos);
                        if (!skipAir || !sourceBlock.is(Blocks.AIR)) {
                            if (sourceBlock != level.getBlockState(destinationPos)) {
                                return OptionalInt.empty();
                            }

                            BlockEntity sourceBlockEntity = level.getBlockEntity(sourcePos);
                            BlockEntity destinationBlockEntity = level.getBlockEntity(destinationPos);
                            if (sourceBlockEntity != null) {
                                if (destinationBlockEntity == null) {
                                    return OptionalInt.empty();
                                }

                                if (destinationBlockEntity.getType() != sourceBlockEntity.getType()) {
                                    return OptionalInt.empty();
                                }

                                if (!sourceBlockEntity.components().equals(destinationBlockEntity.components())) {
                                    return OptionalInt.empty();
                                }

                                TagValueOutput sourceOutput = TagValueOutput.createWithContext(
                                    reporter.forChild(sourceBlockEntity.problemPath()), registryAccess
                                );
                                sourceBlockEntity.saveCustomOnly(sourceOutput);
                                CompoundTag sourceTag = sourceOutput.buildResult();
                                TagValueOutput destinationOutput = TagValueOutput.createWithContext(
                                    reporter.forChild(destinationBlockEntity.problemPath()), registryAccess
                                );
                                destinationBlockEntity.saveCustomOnly(destinationOutput);
                                CompoundTag destinationTag = destinationOutput.buildResult();
                                if (!sourceTag.equals(destinationTag)) {
                                    return OptionalInt.empty();
                                }
                            }

                            count++;
                        }
                    }
                }
            }
        }

        return OptionalInt.of(count);
    }

    private static RedirectModifier<CommandSourceStack> expandOneToOneEntityRelation(Function<Entity, Optional<Entity>> unpacker) {
        return context -> {
            CommandSourceStack source = context.getSource();
            Entity entity = source.getEntity();
            return entity == null ? List.of() : unpacker.apply(entity).filter(e -> !e.isRemoved()).map(e -> List.of(source.withEntity(e))).orElse(List.of());
        };
    }

    private static RedirectModifier<CommandSourceStack> expandOneToManyEntityRelation(Function<Entity, Stream<Entity>> unpacker) {
        return context -> {
            CommandSourceStack source = context.getSource();
            Entity entity = source.getEntity();
            return entity == null ? List.of() : unpacker.apply(entity).filter(e -> !e.isRemoved()).map(source::withEntity).toList();
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRelationOperations(
        CommandNode<CommandSourceStack> execute, LiteralArgumentBuilder<CommandSourceStack> on
    ) {
        return on.then(
                Commands.literal("owner")
                    .fork(
                        execute,
                        expandOneToOneEntityRelation(
                            e -> e instanceof OwnableEntity ownableEntity ? Optional.ofNullable(ownableEntity.getOwner()) : Optional.empty()
                        )
                    )
            )
            .then(
                Commands.literal("leasher")
                    .fork(
                        execute,
                        expandOneToOneEntityRelation(e -> e instanceof Leashable leashable ? Optional.ofNullable(leashable.getLeashHolder()) : Optional.empty())
                    )
            )
            .then(
                Commands.literal("target")
                    .fork(
                        execute,
                        expandOneToOneEntityRelation(e -> e instanceof Targeting targeting ? Optional.ofNullable(targeting.getTarget()) : Optional.empty())
                    )
            )
            .then(
                Commands.literal("attacker")
                    .fork(
                        execute,
                        expandOneToOneEntityRelation(
                            e -> e instanceof Attackable attackable ? Optional.ofNullable(attackable.getLastAttacker()) : Optional.empty()
                        )
                    )
            )
            .then(Commands.literal("vehicle").fork(execute, expandOneToOneEntityRelation(e -> Optional.ofNullable(e.getVehicle()))))
            .then(Commands.literal("controller").fork(execute, expandOneToOneEntityRelation(e -> Optional.ofNullable(e.getControllingPassenger()))))
            .then(
                Commands.literal("origin")
                    .fork(
                        execute,
                        expandOneToOneEntityRelation(e -> e instanceof TraceableEntity traceable ? Optional.ofNullable(traceable.getOwner()) : Optional.empty())
                    )
            )
            .then(Commands.literal("passengers").fork(execute, expandOneToManyEntityRelation(e -> e.getPassengers().stream())));
    }

    private static CommandSourceStack spawnEntityAndRedirect(CommandSourceStack source, Holder.Reference<EntityType<?>> type) throws CommandSyntaxException {
        Entity entity = SummonCommand.createEntity(source, type, source.getPosition(), new CompoundTag(), true);
        return source.withEntity(entity);
    }

    public static <T extends ExecutionCommandSource<T>> void scheduleFunctionConditionsAndTest(
        T originalSource,
        List<T> currentSources,
        Function<T, T> functionContextModifier,
        IntPredicate check,
        ContextChain<T> currentStep,
        @Nullable CompoundTag parameters,
        ExecutionControl<T> output,
        InCommandFunction<CommandContext<T>, Collection<CommandFunction<T>>> functionGetter,
        ChainModifiers modifiers
    ) {
        List<T> filteredSources = new ArrayList<>(currentSources.size());

        Collection<CommandFunction<T>> functionsToRun;
        try {
            functionsToRun = functionGetter.apply(currentStep.getTopContext().copyFor(originalSource));
        } catch (CommandSyntaxException e) {
            originalSource.handleError(e, modifiers.isForked(), output.tracer());
            return;
        }

        int functionCount = functionsToRun.size();
        if (functionCount != 0) {
            List<InstantiatedFunction<T>> instantiatedFunctions = new ArrayList<>(functionCount);

            try {
                for (CommandFunction<T> function : functionsToRun) {
                    try {
                        instantiatedFunctions.add(function.instantiate(parameters, originalSource.dispatcher()));
                    } catch (FunctionInstantiationException e) {
                        throw ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(function.id(), e.messageComponent());
                    }
                }
            } catch (CommandSyntaxException e) {
                originalSource.handleError(e, modifiers.isForked(), output.tracer());
            }

            for (T source : currentSources) {
                T newFunctionContext = (T)functionContextModifier.apply(source.clearCallbacks());
                CommandResultCallback functionCallback = (success, result) -> {
                    if (check.test(result)) {
                        filteredSources.add(source);
                    }
                };
                output.queueNext(new IsolatedCall<>(o -> {
                    for (InstantiatedFunction<T> function : instantiatedFunctions) {
                        o.queueNext(new CallFunction<>(function, o.currentFrame().returnValueConsumer(), true).bind(newFunctionContext));
                    }

                    o.queueNext(FallthroughTask.instance());
                }, functionCallback));
            }

            ContextChain<T> nextStage = currentStep.nextStage();
            String input = currentStep.getTopContext().getInput();
            output.queueNext(new BuildContexts.Continuation<>(input, nextStage, modifiers, originalSource, filteredSources));
        }
    }

    @FunctionalInterface
    private interface CommandNumericPredicate {
        int test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    @FunctionalInterface
    private interface CommandPredicate {
        boolean test(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    private static class ExecuteIfFunctionCustomModifier implements CustomModifierExecutor.ModifierAdapter<CommandSourceStack> {
        private final IntPredicate check;

        private ExecuteIfFunctionCustomModifier(boolean check) {
            this.check = check ? value -> value != 0 : value -> value == 0;
        }

        public void apply(
            CommandSourceStack originalSource,
            List<CommandSourceStack> currentSources,
            ContextChain<CommandSourceStack> currentStep,
            ChainModifiers modifiers,
            ExecutionControl<CommandSourceStack> output
        ) {
            ExecuteCommand.scheduleFunctionConditionsAndTest(
                originalSource,
                currentSources,
                FunctionCommand::modifySenderForExecution,
                this.check,
                currentStep,
                null,
                output,
                c -> FunctionArgument.getFunctions(c, "name"),
                modifiers
            );
        }
    }

    @FunctionalInterface
    private interface IntBiPredicate {
        boolean test(int a, int b);
    }
}
