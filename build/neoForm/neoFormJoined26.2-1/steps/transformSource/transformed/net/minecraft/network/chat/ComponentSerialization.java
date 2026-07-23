package net.minecraft.network.chat;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.ScoreContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;

public class ComponentSerialization {
    public static final Codec<Component> CODEC = Codec.recursive("Component", ComponentSerialization::createCodec);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_STREAM_CODEC.apply(
        ByteBufCodecs::optional
    );
    public static final StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC = ByteBufCodecs.fromCodecTrusted(CODEC);

    public static Codec<Component> flatRestrictedCodec(int maxFlatSize) {
        return new Codec<Component>() {
            @Override
            public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
                return ComponentSerialization.CODEC
                    .decode(ops, input)
                    .flatMap(
                        pair -> this.isTooLarge(ops, pair.getFirst())
                            ? DataResult.error(() -> "Component was too large: greater than max size " + maxFlatSize)
                            : DataResult.success((Pair<Component, T>)pair)
                    );
            }

            public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
                return ComponentSerialization.CODEC.encodeStart(ops, input);
            }

            private <T> boolean isTooLarge(DynamicOps<T> ops, Component input) {
                DataResult<JsonElement> json = ComponentSerialization.CODEC.encodeStart(asJsonOps(ops), input);
                return json.isSuccess() && GsonHelper.encodesLongerThan(json.getOrThrow(), maxFlatSize);
            }

            private static <T> DynamicOps<JsonElement> asJsonOps(DynamicOps<T> ops) {
                return ops instanceof RegistryOps<T> registryOps ? registryOps.withParent(JsonOps.INSTANCE) : JsonOps.INSTANCE;
            }
        };
    }

    private static MutableComponent createFromList(List<Component> list) {
        MutableComponent result = list.get(0).copy();

        for (int i = 1; i < list.size(); i++) {
            result.append(list.get(i));
        }

        return result;
    }

    public static <T> MapCodec<T> createLegacyComponentMatcher(
        ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends T>> types, Function<T, MapCodec<? extends T>> codecGetter, String typeFieldName
    ) {
        MapCodec<T> compactCodec = new ComponentSerialization.FuzzyCodec<>(types.values(), codecGetter);
        MapCodec<T> discriminatorCodec = types.codec(Codec.STRING).dispatchMap(typeFieldName, codecGetter, c -> c);
        MapCodec<T> contentsCodec = new ComponentSerialization.StrictEither<>(typeFieldName, discriminatorCodec, compactCodec);
        return ExtraCodecs.orCompressed(contentsCodec, discriminatorCodec);
    }

    private static Codec<Component> createCodec(Codec<Component> topSerializer) {
        ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> contentTypes = new ExtraCodecs.LateBoundIdMapper<>();
        bootstrap(contentTypes);
        MapCodec<ComponentContents> compressedContentsCodec = createLegacyComponentMatcher(contentTypes, ComponentContents::codec, "type");
        Codec<Component> fullCodec = RecordCodecBuilder.create(
            i -> i.group(
                    compressedContentsCodec.forGetter(Component::getContents),
                    ExtraCodecs.nonEmptyList(topSerializer.listOf()).optionalFieldOf("extra", List.of()).forGetter(Component::getSiblings),
                    Style.Serializer.MAP_CODEC.forGetter(Component::getStyle)
                )
                .apply(i, MutableComponent::new)
        );
        return Codec.either(Codec.either(Codec.STRING, ExtraCodecs.nonEmptyList(topSerializer.listOf())), fullCodec)
            .xmap(
                specialOrComponent -> specialOrComponent.map(
                    special -> special.map(Component::literal, ComponentSerialization::createFromList), c -> (Component)c
                ),
                component -> {
                    String text = component.tryCollapseToString();
                    return text != null ? Either.left(Either.left(text)) : Either.right(component);
                }
            );
    }

    private static void bootstrap(ExtraCodecs.LateBoundIdMapper<String, MapCodec<? extends ComponentContents>> contentTypes) {
        contentTypes.put("text", PlainTextContents.MAP_CODEC);
        contentTypes.put("translatable", TranslatableContents.MAP_CODEC);
        contentTypes.put("keybind", KeybindContents.MAP_CODEC);
        contentTypes.put("score", ScoreContents.MAP_CODEC);
        contentTypes.put("selector", SelectorContents.MAP_CODEC);
        contentTypes.put("nbt", NbtContents.MAP_CODEC);
        contentTypes.put("object", ObjectContents.MAP_CODEC);
        contentTypes.put("neoforge:inserting", net.neoforged.neoforge.common.util.InsertingContents.MAP_CODEC);
    }

    private static class FuzzyCodec<T> extends MapCodec<T> {
        private final Collection<MapCodec<? extends T>> codecs;
        private final Function<T, ? extends MapEncoder<? extends T>> encoderGetter;

        public FuzzyCodec(Collection<MapCodec<? extends T>> codecs, Function<T, ? extends MapEncoder<? extends T>> encoderGetter) {
            this.codecs = codecs;
            this.encoderGetter = encoderGetter;
        }

        @Override
        public <S> DataResult<T> decode(DynamicOps<S> ops, MapLike<S> input) {
            for (MapDecoder<? extends T> codec : this.codecs) {
                DataResult<? extends T> result = codec.decode(ops, input);
                if (result.result().isPresent()) {
                    return (DataResult<T>)result;
                }
            }

            return DataResult.error(() -> "No matching codec found");
        }

        @Override
        public <S> RecordBuilder<S> encode(T input, DynamicOps<S> ops, RecordBuilder<S> prefix) {
            MapEncoder<T> encoder = (MapEncoder<T>)this.encoderGetter.apply(input);
            return encoder.encode(input, ops, prefix);
        }

        @Override
        public <S> Stream<S> keys(DynamicOps<S> ops) {
            return this.codecs.stream().flatMap(c -> c.keys(ops)).distinct();
        }

        @Override
        public String toString() {
            return "FuzzyCodec[" + this.codecs + "]";
        }
    }

    private static class StrictEither<T> extends MapCodec<T> {
        private final String typeFieldName;
        private final MapCodec<T> typed;
        private final MapCodec<T> fuzzy;

        public StrictEither(String typeFieldName, MapCodec<T> typed, MapCodec<T> fuzzy) {
            this.typeFieldName = typeFieldName;
            this.typed = typed;
            this.fuzzy = fuzzy;
        }

        @Override
        public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
            return input.get(this.typeFieldName) != null ? this.typed.decode(ops, input) : this.fuzzy.decode(ops, input);
        }

        @Override
        public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
            return this.fuzzy.encode(input, ops, prefix);
        }

        @Override
        public <T1> Stream<T1> keys(DynamicOps<T1> ops) {
            return Stream.concat(this.typed.keys(ops), this.fuzzy.keys(ops)).distinct();
        }
    }
}
