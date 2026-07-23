package net.minecraft.server.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.ThreadFactory;
import org.jspecify.annotations.Nullable;

public abstract class EventLoopGroupHolder {
    private static final EventLoopGroupHolder NIO = new EventLoopGroupHolder("NIO", NioSocketChannel.class, NioServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return NioIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder EPOLL = new EventLoopGroupHolder("Epoll", EpollSocketChannel.class, EpollServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return EpollIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder KQUEUE = new EventLoopGroupHolder("Kqueue", KQueueSocketChannel.class, KQueueServerSocketChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return KQueueIoHandler.newFactory();
        }
    };
    private static final EventLoopGroupHolder LOCAL = new EventLoopGroupHolder("Local", LocalChannel.class, LocalServerChannel.class) {
        @Override
        protected IoHandlerFactory ioHandlerFactory() {
            return LocalIoHandler.newFactory();
        }
    };
    private final String type;
    private final Class<? extends Channel> channelCls;
    private final Class<? extends ServerChannel> serverChannelCls;
    private volatile @Nullable EventLoopGroup group;
    private volatile @Nullable EventLoopGroup serverGroup;

    public static EventLoopGroupHolder remote(boolean allowNativeTransport) {
        if (allowNativeTransport) {
            if (KQueue.isAvailable()) {
                return KQUEUE;
            }

            if (Epoll.isAvailable()) {
                return EPOLL;
            }
        }

        return NIO;
    }

    public static EventLoopGroupHolder local() {
        return LOCAL;
    }

    private EventLoopGroupHolder(String type, Class<? extends Channel> channelCls, Class<? extends ServerChannel> serverChannelCls) {
        this.type = type;
        this.channelCls = channelCls;
        this.serverChannelCls = serverChannelCls;
    }

    private ThreadFactory createThreadFactory() {
        return new ThreadFactoryBuilder().setNameFormat("Netty " + this.type + " IO #%d").setDaemon(true).build();
    }

    protected abstract IoHandlerFactory ioHandlerFactory();

    private EventLoopGroup createEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(this.createThreadFactory(), this.ioHandlerFactory());
    }

    // Neo: Use a separate event group for the server-side which allows us to identify server threads
    public EventLoopGroup serverEventLoopGroup() {
        var eventloopgroup = this.serverGroup;
        if (eventloopgroup == null) {
            synchronized (this) {
                eventloopgroup = this.serverGroup;
                if (eventloopgroup == null) {
                    var threadFactory = new ThreadFactoryBuilder().setNameFormat("Netty " + this.type + " IO #%d").setThreadFactory(net.neoforged.fml.util.thread.SidedThreadGroups.SERVER).setDaemon(true).build();
                    eventloopgroup = new MultiThreadIoEventLoopGroup(threadFactory, this.ioHandlerFactory());
                    this.serverGroup = eventloopgroup;
                }
            }
        }

        return eventloopgroup;
    }

    public EventLoopGroup eventLoopGroup() {
        EventLoopGroup result = this.group;
        if (result == null) {
            synchronized (this) {
                result = this.group;
                if (result == null) {
                    result = this.createEventLoopGroup();
                    this.group = result;
                }
            }
        }

        return result;
    }

    public Class<? extends Channel> channelCls() {
        return this.channelCls;
    }

    public Class<? extends ServerChannel> serverChannelCls() {
        return this.serverChannelCls;
    }
}
