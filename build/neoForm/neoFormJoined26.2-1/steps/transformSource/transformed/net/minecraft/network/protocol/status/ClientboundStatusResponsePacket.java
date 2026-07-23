package net.minecraft.network.protocol.status;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.RegistryOps;

public record ClientboundStatusResponsePacket(ServerStatus status, @org.jspecify.annotations.Nullable String cachedStatus) implements Packet<ClientStatusPacketListener> {
    private static final RegistryOps<JsonElement> OPS = RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE);
    public static final StreamCodec<ByteBuf, ClientboundStatusResponsePacket> VANILLA_STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.lenientJson(32767).apply(ByteBufCodecs.fromCodec(OPS, ServerStatus.CODEC)),
        ClientboundStatusResponsePacket::status,
        ClientboundStatusResponsePacket::new
    );
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ClientboundStatusResponsePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                if (packet.cachedStatus != null) {
                    buffer.writeUtf(packet.cachedStatus);
                } else {
                    VANILLA_STREAM_CODEC.encode(buffer, packet);
                }
            },
            VANILLA_STREAM_CODEC::decode
    );

    public ClientboundStatusResponsePacket(ServerStatus status) {
        this(status, null);
    }

    @Override
    public PacketType<ClientboundStatusResponsePacket> type() {
        return StatusPacketTypes.CLIENTBOUND_STATUS_RESPONSE;
    }

    public void handle(ClientStatusPacketListener listener) {
        listener.handleStatusResponse(this);
    }
}
