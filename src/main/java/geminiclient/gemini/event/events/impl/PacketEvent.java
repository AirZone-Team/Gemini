package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;
import geminiclient.gemini.event.events.impl.enums.IOEnum;
import net.minecraft.network.protocol.Packet;

public class PacketEvent extends CancellableEvent {
    public Packet<?> getPacket() {
        return packet;
    }

    public void setPacket(Packet<?> packet) {
        this.packet = packet;
    }

    private Packet<?> packet;

    public IOEnum getIoEnum() {
        return ioEnum;
    }

    private final IOEnum ioEnum;

    public PacketEvent(Packet<?> packet,IOEnum ioEnum) {
        this.packet = packet;
        this.ioEnum = ioEnum;
    }
}
