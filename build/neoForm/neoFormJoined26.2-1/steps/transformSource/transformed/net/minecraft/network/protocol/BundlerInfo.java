package net.minecraft.network.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.PacketListener;
import org.jspecify.annotations.Nullable;

public interface BundlerInfo {
    int BUNDLE_SIZE_LIMIT = 4096;

    static <T extends PacketListener, P extends BundlePacket<? super T>> BundlerInfo createForPacket(
        PacketType<P> bundlePacketType, Function<Iterable<Packet<? super T>>, P> constructor, BundleDelimiterPacket<? super T> delimiterPacket
    ) {
        return new BundlerInfo() {
            @Override
            public void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> output) {
                if (packet.type() == bundlePacketType) {
                    P bundlerPacket = (P)packet;
                    output.accept(delimiterPacket);
                    bundlerPacket.subPackets().forEach(output);
                    output.accept(delimiterPacket);
                } else {
                    output.accept(packet);
                }
            }

            @Override
            public void unbundlePacket(Packet<?> bundlePacket, Consumer<Packet<?>> packetSender, io.netty.channel.ChannelHandlerContext context) {
                if (bundlePacket.type() == bundlePacketType) {
                    P p = (P)bundlePacket;
                    java.util.List<Packet<?>> packets = net.neoforged.neoforge.network.registration.NetworkRegistry.filterGameBundlePackets(context, p.subPackets());
                    if (packets.isEmpty()) {
                        return;
                    }
                    if (packets.size() == 1) {
                        packetSender.accept(packets.get(0));
                        return;
                    }
                    packetSender.accept(delimiterPacket);
                    packets.forEach(packetSender);
                    packetSender.accept(delimiterPacket);
                } else {
                    packetSender.accept(bundlePacket);
                }
            }

            @Override
            public BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet) {
                return packet == delimiterPacket ? new BundlerInfo.Bundler() {
                    private final List<Packet<? super T>> bundlePackets = new ArrayList<>();

                    @Override
                    public @Nullable Packet<?> addPacket(Packet<?> packet) {
                        if (packet == delimiterPacket) {
                            return constructor.apply(this.bundlePackets);
                        }

                        Packet<T> castPacket = (Packet<T>)packet;
                        if (this.bundlePackets.size() >= 4096) {
                            throw new IllegalStateException("Too many packets in a bundle");
                        }

                        this.bundlePackets.add(castPacket);
                        return null;
                    }
                } : null;
            }
        };
    }

    /**
     * @deprecated Use {@link #unbundlePacket(Packet, Consumer, io.netty.channel.ChannelHandlerContext)} instead, as it supports packet filtering and is more efficient.
     */
    @Deprecated
    void unbundlePacket(Packet<?> packet, Consumer<Packet<?>> output);

    /**
     * Unwrap and flattens a bundle packet.
     * Then sends the packets contained in the bundle, bracketing them in delimiter packets if need be.
     *
     * @param bundlePacket The bundle packet to write.
     * @param packetSender The packet sender.
     * @param context The network context.
     * @implNote This implementation should filter out packets which are not sendable on the current context, however to preserve compatibility the default implementation does not do this.
     */
    default void unbundlePacket(Packet<?> bundlePacket, Consumer<Packet<?>> packetSender, io.netty.channel.ChannelHandlerContext context) {
        unbundlePacket(bundlePacket, packetSender);
    }

    BundlerInfo.@Nullable Bundler startPacketBundling(Packet<?> packet);

    interface Bundler {
        @Nullable Packet<?> addPacket(Packet<?> packet);
    }
}
