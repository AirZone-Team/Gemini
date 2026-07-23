package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import org.jspecify.annotations.Nullable;

public class RuleBasedStateProvider extends BlockStateProvider {
    public static final MapCodec<RuleBasedStateProvider> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
                BlockStateProvider.CODEC.optionalFieldOf("fallback").forGetter(provider -> Optional.ofNullable(provider.fallback)),
                RuleBasedStateProvider.Rule.CODEC.listOf().fieldOf("rules").forGetter(p -> p.rules)
            )
            .apply(i, RuleBasedStateProvider::new)
    );
    private final @Nullable BlockStateProvider fallback;
    private final List<RuleBasedStateProvider.Rule> rules;

    public RuleBasedStateProvider(@Nullable BlockStateProvider fallback, List<RuleBasedStateProvider.Rule> rules) {
        this.fallback = fallback;
        this.rules = rules;
    }

    private RuleBasedStateProvider(Optional<BlockStateProvider> fallback, List<RuleBasedStateProvider.Rule> rules) {
        this(fallback.orElse(null), rules);
    }

    public static RuleBasedStateProvider ifTrueThenProvide(BlockPredicate ifTrue, Block thenProvide) {
        return ifTrueThenProvide(ifTrue, BlockStateProvider.simple(thenProvide));
    }

    public static RuleBasedStateProvider ifTrueThenProvide(BlockPredicate ifTrue, BlockStateProvider thenProvide) {
        return new RuleBasedStateProvider((BlockStateProvider)null, List.of(new RuleBasedStateProvider.Rule(ifTrue, thenProvide)));
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.RULE_BASED_STATE_PROVIDER;
    }

    @Override
    public BlockState getState(WorldGenLevel level, RandomSource random, BlockPos pos) {
        BlockState result = this.getOptionalState(level, random, pos);
        return result != null ? result : level.getBlockState(pos);
    }

    @Override
    public @Nullable BlockState getOptionalState(WorldGenLevel level, RandomSource random, BlockPos pos) {
        for (RuleBasedStateProvider.Rule rule : this.rules) {
            if (rule.ifTrue().test(level, pos)) {
                return rule.then().getState(level, random, pos);
            }
        }

        return this.fallback == null ? null : this.fallback.getState(level, random, pos);
    }

    public static RuleBasedStateProvider.Builder builder() {
        return new RuleBasedStateProvider.Builder(null);
    }

    public static RuleBasedStateProvider.Builder builder(@Nullable BlockStateProvider fallback) {
        return new RuleBasedStateProvider.Builder(fallback);
    }

    public static class Builder {
        private final @Nullable BlockStateProvider fallback;
        private final List<RuleBasedStateProvider.Rule> rules = new ArrayList<>();

        public Builder(@Nullable BlockStateProvider fallback) {
            this.fallback = fallback;
        }

        public RuleBasedStateProvider.Builder ifTrueThenProvide(BlockPredicate ifTrue, BlockStateProvider thenProvide) {
            this.rules.add(new RuleBasedStateProvider.Rule(ifTrue, thenProvide));
            return this;
        }

        public RuleBasedStateProvider.Builder ifTrueThenProvide(BlockPredicate ifTrue, Block thenProvide) {
            this.rules.add(new RuleBasedStateProvider.Rule(ifTrue, BlockStateProvider.simple(thenProvide)));
            return this;
        }

        public RuleBasedStateProvider.Builder ifTrueThenProvide(BlockPredicate ifTrue, BlockState thenProvide) {
            this.rules.add(new RuleBasedStateProvider.Rule(ifTrue, BlockStateProvider.simple(thenProvide)));
            return this;
        }

        public RuleBasedStateProvider build() {
            return new RuleBasedStateProvider(this.fallback, this.rules);
        }
    }

    public record Rule(BlockPredicate ifTrue, BlockStateProvider then) {
        public static final Codec<RuleBasedStateProvider.Rule> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    BlockPredicate.CODEC.fieldOf("if_true").forGetter(RuleBasedStateProvider.Rule::ifTrue),
                    BlockStateProvider.CODEC.fieldOf("then").forGetter(RuleBasedStateProvider.Rule::then)
                )
                .apply(i, RuleBasedStateProvider.Rule::new)
        );
    }
}
