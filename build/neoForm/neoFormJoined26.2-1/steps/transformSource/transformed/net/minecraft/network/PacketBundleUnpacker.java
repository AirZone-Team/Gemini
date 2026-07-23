package net.minecraft.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;

public class PacketBundleUnpacker extends MessageToMessageEncoder<Packet<?>> {
    private final BundlerInfo bundlerInfo;

    public PacketBundleUnpacker(BundlerInfo bundlerInfo) {
        this.bundlerInfo = bundlerInfo;
    }

    protected void encode(ChannelHandlerContext ctx, Packet<?> msg, List<Object> out) throws Exception {
        this.bundlerInfo.unbundlePacket(msg, out::add, ctx);
        if (msg.isTerminal()) {
            ctx.pipeline().remove(ctx.name());
        }
    }
}
