package net.minecraft.network.protocol.common.custom;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.resources.Identifier;

public interface CustomPacketPayload {
    CustomPacketPayload.Type<? extends CustomPacketPayload> type();

    static <B extends ByteBuf, T extends CustomPacketPayload> StreamCodec<B, T> codec(StreamMemberEncoder<B, T> writer, StreamDecoder<B, T> reader) {
        return StreamCodec.ofMember(writer, reader);
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String id) {
        return new CustomPacketPayload.Type<>(Identifier.withDefaultNamespace(id));
    }

    static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> codec(
        final CustomPacketPayload.FallbackProvider<B> fallback, List<CustomPacketPayload.TypeAndCodec<? super B, ?>> types, net.minecraft.network.ConnectionProtocol protocol, net.minecraft.network.protocol.PacketFlow packetFlow
    ) {
        final Map<Identifier, StreamCodec<? super B, ? extends CustomPacketPayload>> idToType = types.stream()
            .collect(Collectors.toUnmodifiableMap(t -> t.type().id(), CustomPacketPayload.TypeAndCodec::codec));
        return new StreamCodec<B, CustomPacketPayload>() {
            private StreamCodec<? super B, ? extends CustomPacketPayload> findCodec(Identifier typeId) {
                StreamCodec<? super B, ? extends CustomPacketPayload> codec = idToType.get(typeId);
                if (codec == null) codec = net.neoforged.neoforge.network.registration.NetworkRegistry.getCodec(typeId, protocol, packetFlow);
                return codec != null ? codec : fallback.create(typeId);
            }

            private <T extends CustomPacketPayload> void writeCap(B output, CustomPacketPayload.Type<T> type, CustomPacketPayload payload) {
                output.writeIdentifier(type.id());
                StreamCodec<B, T> codec = (StreamCodec<B, T>)this.findCodec(type.id);
                try {
                codec.encode(output, (T)payload);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed encoding custom payload " + type.id() + ": " + e, e); // Make it easier to debug which mod payload failed to be encoded
                }
            }

            public void encode(B output, CustomPacketPayload value) {
                this.writeCap(output, value.type(), value);
            }

            public CustomPacketPayload decode(B input) {
                Identifier identifier = input.readIdentifier();
                try {
                    return (CustomPacketPayload)this.findCodec(identifier).decode(input);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Failed decoding custom payload " + identifier + ": " + e, e); // Make it easier to debug which mod payload failed to be decoded
                }
            }
        };
    }

    /**
     * {@return the vanilla clientbound packet representation of this payload}
     */
    default net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket toVanillaClientbound() {
        return new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(this);
    }

    /**
     * {@return the vanilla serverbound packet representation of this payload}
     */
    default net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket toVanillaServerbound() {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(this);
    }

    interface FallbackProvider<B extends FriendlyByteBuf> {
        StreamCodec<B, ? extends CustomPacketPayload> create(Identifier typeId);
    }

    record Type<T extends CustomPacketPayload>(Identifier id) {
    }

    record TypeAndCodec<B extends FriendlyByteBuf, T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<B, T> codec) {
    }
}
