package geminiclient.mixin;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.event.events.impl.PacketEvent;
import geminiclient.gemini.event.events.impl.enums.IOEnum;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",at = @At("HEAD"), cancellable = true)
    public void callPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        PacketEvent packetEvent = new PacketEvent(packet, IOEnum.In);
        Gemini.eventManager.call(packetEvent);
        if (packetEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",at = @At("HEAD"), cancellable = true)
    public void callPacketS(Packet<?> packet, CallbackInfo ci) {
        PacketEvent packetEvent = new PacketEvent(packet,IOEnum.Out);
        Gemini.eventManager.call(packetEvent);
        if (packetEvent.isCancelled()) {
            ci.cancel();
        }
    }
}
