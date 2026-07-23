package net.minecraft.network;

import com.google.common.collect.Queues;
import com.mojang.logging.LogUtils;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.ReportedException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import org.slf4j.Logger;

import net.neoforged.neoforge.network.handling.QueuedPacket;

public class PacketProcessor implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Queue<net.neoforged.neoforge.network.handling.QueuedPacket> packetsToBeHandled = Queues.newConcurrentLinkedQueue();
    private final Thread runningThread;
    private boolean closed;

    public PacketProcessor(Thread runningThread) {
        this.runningThread = runningThread;
    }

    public boolean isSameThread() {
        return Thread.currentThread() == this.runningThread;
    }

    public <T extends PacketListener> void scheduleIfPossible(T listener, Packet<T> packet) {
        if (this.closed) {
            throw new RejectedExecutionException("Server already shutting down");
        }

        this.packetsToBeHandled.add(new PacketProcessor.ListenerAndPacket<>(listener, packet));
    }

    /**
     * Neo: Enqueue a main thread task from a custom payload packet handler.
     * Use via {@link net.neoforged.neoforge.network.handling.IPayloadContext#enqueueWork}
     */
    @org.jetbrains.annotations.ApiStatus.Internal
    public void scheduleIfPossible(Runnable task) {
        if (this.closed) {
            throw new RejectedExecutionException("Server already shutting down");
        } else {
            this.packetsToBeHandled.add(new net.neoforged.neoforge.network.handling.QueuedPacket.CustomPayload(task));
        }
    }

    public void processQueuedPackets() {
        if (!this.closed) {
            while (!this.packetsToBeHandled.isEmpty()) {
                this.packetsToBeHandled.poll().handle();
            }
        }
    }

    @Override
    public void close() {
        this.closed = true;
    }

    private record ListenerAndPacket<T extends PacketListener>(T listener, Packet<T> packet) implements QueuedPacket {
        public void handle() {
            if (this.listener.shouldHandleMessage(this.packet)) {
                try {
                    this.packet.handle(this.listener);
                } catch (Exception e) {
                    if (e instanceof ReportedException re && re.getCause() instanceof OutOfMemoryError) {
                        throw PacketUtils.makeReportedException(e, this.packet, this.listener);
                    }

                    this.listener.onPacketError(this.packet, e);
                }
            } else {
                PacketProcessor.LOGGER.debug("Ignoring packet due to disconnection: {}", this.packet);
            }
        }
    }
}
