package net.minecraft.network.protocol;

import net.neoforged.neoforge.common.extensions.IPacketFlowExtension;

public enum PacketFlow implements IPacketFlowExtension {
    SERVERBOUND("serverbound"),
    CLIENTBOUND("clientbound");

    private final String id;

    PacketFlow(String id) {
        this.id = id;
    }

    public PacketFlow getOpposite() {
        return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND;
    }

    public String id() {
        return this.id;
    }
}
