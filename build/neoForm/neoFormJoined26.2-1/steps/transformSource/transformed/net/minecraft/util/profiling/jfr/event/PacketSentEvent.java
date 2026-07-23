package net.minecraft.util.profiling.jfr.event;

import java.net.SocketAddress;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("minecraft.PacketSent")
@Label("Network Packet Sent")
public class PacketSentEvent extends PacketEvent {
    public static final String NAME = "minecraft.PacketSent";
    public static final EventType TYPE = EventType.getEventType(PacketSentEvent.class);

    public PacketSentEvent(String protocolId, String packetDirection, String packetId, SocketAddress remoteAddress, int writtenBytes) {
        super(protocolId, packetDirection, packetId, remoteAddress, writtenBytes);
    }
}
