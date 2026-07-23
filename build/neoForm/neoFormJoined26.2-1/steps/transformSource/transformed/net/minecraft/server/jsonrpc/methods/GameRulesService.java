package net.minecraft.server.jsonrpc.methods;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleType;

public class GameRulesService {
    public static List<GameRulesService.GameRuleUpdate<?>> get(MinecraftApi minecraftApi) {
        List<GameRulesService.GameRuleUpdate<?>> rules = new ArrayList<>();
        minecraftApi.gameRuleService().getAvailableGameRules().forEach(gameRule -> addGameRule(minecraftApi, (GameRule<?>)gameRule, rules));
        return rules;
    }

    private static <T> void addGameRule(MinecraftApi minecraftApi, GameRule<T> gameRule, List<GameRulesService.GameRuleUpdate<?>> rules) {
        T value = minecraftApi.gameRuleService().getRuleValue(gameRule);
        rules.add(getTypedRule(minecraftApi, gameRule, value));
    }

    public static <T> GameRulesService.GameRuleUpdate<T> getTypedRule(MinecraftApi minecraftApi, GameRule<T> gameRule, T value) {
        return minecraftApi.gameRuleService().getTypedRule(gameRule, value);
    }

    public static <T> GameRulesService.GameRuleUpdate<T> update(MinecraftApi minecraftApi, GameRulesService.GameRuleUpdate<T> update, ClientInfo clientInfo) {
        return minecraftApi.gameRuleService().updateGameRule(update, clientInfo);
    }

    public record GameRuleUpdate<T>(GameRule<T> gameRule, T value) {
        public static final Codec<GameRulesService.GameRuleUpdate<?>> TYPED_CODEC = BuiltInRegistries.GAME_RULE
            .byNameCodec()
            .dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueAndTypeCodec);
        public static final Codec<GameRulesService.GameRuleUpdate<?>> CODEC = BuiltInRegistries.GAME_RULE
            .byNameCodec()
            .dispatch("key", GameRulesService.GameRuleUpdate::gameRule, GameRulesService.GameRuleUpdate::getValueCodec);

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueCodec(GameRule<T> gameRule) {
            return gameRule.valueCodec()
                .fieldOf("value")
                .xmap(value -> new GameRulesService.GameRuleUpdate<>(gameRule, (T)value), GameRulesService.GameRuleUpdate::value);
        }

        private static <T> MapCodec<? extends GameRulesService.GameRuleUpdate<T>> getValueAndTypeCodec(GameRule<T> gameRule) {
            return RecordCodecBuilder.mapCodec(
                i -> i.group(
                        StringRepresentable.fromEnum(GameRuleType::values)
                            .fieldOf("type")
                            .forGetter(r -> r.gameRule.gameRuleType()),
                        gameRule.valueCodec()
                            .fieldOf("value")
                            .forGetter(GameRulesService.GameRuleUpdate::value)
                    )
                    .apply(i, (type, value) -> getUntypedRule(gameRule, type, value))
            );
        }

        private static <T> GameRulesService.GameRuleUpdate<T> getUntypedRule(GameRule<T> gameRule, GameRuleType readType, T value) {
            if (gameRule.gameRuleType() != readType) {
                throw new InvalidParameterJsonRpcException(
                    "Stated type \"" + readType + "\" mismatches with actual type \"" + gameRule.gameRuleType() + "\" of gamerule \"" + gameRule.id() + "\""
                );
            } else {
                return new GameRulesService.GameRuleUpdate<>(gameRule, value);
            }
        }
    }
}
