package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.syncher.SynchedEntityData;

public record ClientboundSetEntityDataPacket(int id, List<SynchedEntityData.DataValue<?>> packedItems) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSetEntityDataPacket> STREAM_CODEC = Packet.codec(
        ClientboundSetEntityDataPacket::write, ClientboundSetEntityDataPacket::new
    );
    public static final int EOF_MARKER = 255;

    private ClientboundSetEntityDataPacket(RegistryFriendlyByteBuf input) {
        this(input.readVarInt(), unpack(input));
    }

    private static void pack(List<SynchedEntityData.DataValue<?>> items, RegistryFriendlyByteBuf output) {
        for (SynchedEntityData.DataValue<?> item : items) {
            item.write(output);
        }

        output.writeByte(255);
    }

    private static List<SynchedEntityData.DataValue<?>> unpack(RegistryFriendlyByteBuf input) {
        List<SynchedEntityData.DataValue<?>> result = new ArrayList<>();

        int id;
        while ((id = input.readUnsignedByte()) != 255) {
            result.add(SynchedEntityData.DataValue.read(input, id));
        }

        return result;
    }

    private void write(RegistryFriendlyByteBuf output) {
        output.writeVarInt(this.id);
        pack(this.packedItems, output);
    }

    @Override
    public PacketType<ClientboundSetEntityDataPacket> type() {
        return GamePacketTypes.CLIENTBOUND_SET_ENTITY_DATA;
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleSetEntityData(this);
    }
}
