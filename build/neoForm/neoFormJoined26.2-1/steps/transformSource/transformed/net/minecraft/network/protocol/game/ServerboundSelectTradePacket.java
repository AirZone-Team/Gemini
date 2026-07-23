package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundSelectTradePacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSelectTradePacket> STREAM_CODEC = Packet.codec(
        ServerboundSelectTradePacket::write, ServerboundSelectTradePacket::new
    );
    private final int item;

    public ServerboundSelectTradePacket(int item) {
        this.item = item;
    }

    private ServerboundSelectTradePacket(FriendlyByteBuf input) {
        this.item = input.readVarInt();
    }

    private void write(FriendlyByteBuf output) {
        output.writeVarInt(this.item);
    }

    @Override
    public PacketType<ServerboundSelectTradePacket> type() {
        return GamePacketTypes.SERVERBOUND_SELECT_TRADE;
    }

    public void handle(ServerGamePacketListener listener) {
        listener.handleSelectTrade(this);
    }

    public int getItem() {
        return this.item;
    }
}
