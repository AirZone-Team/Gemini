package net.minecraft.core.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Map.Entry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TypedDataComponent<T>(DataComponentType<T> type, T value) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, TypedDataComponent<?>>() {
        public TypedDataComponent<?> decode(RegistryFriendlyByteBuf input) {
            DataComponentType<?> type = DataComponentType.STREAM_CODEC.decode(input);
            return decodeTyped(input, type);
        }

        private static <T> TypedDataComponent<T> decodeTyped(RegistryFriendlyByteBuf input, DataComponentType<T> type) {
            return new TypedDataComponent<>(type, type.streamCodec().decode(input));
        }

        public void encode(RegistryFriendlyByteBuf output, TypedDataComponent<?> value) {
            encodeCap(output, value);
        }

        private static <T> void encodeCap(RegistryFriendlyByteBuf output, TypedDataComponent<T> component) {
            DataComponentType.STREAM_CODEC.encode(output, component.type());
            component.type().streamCodec().encode(output, component.value());
        }
    };

    static TypedDataComponent<?> fromEntryUnchecked(Entry<DataComponentType<?>, Object> entry) {
        return createUnchecked(entry.getKey(), entry.getValue());
    }

    public static <T> TypedDataComponent<T> createUnchecked(DataComponentType<T> type, Object value) {
        return new TypedDataComponent<>(type, (T)value);
    }

    public void applyTo(PatchedDataComponentMap components) {
        components.set(this.type, this.value);
    }

    public <D> DataResult<D> encodeValue(DynamicOps<D> ops) {
        Codec<T> codec = this.type.codec();
        return codec == null ? DataResult.error(() -> "Component of type " + this.type + " is not encodable") : codec.encodeStart(ops, this.value);
    }

    @Override
    public String toString() {
        return this.type + "=>" + this.value;
    }
}
