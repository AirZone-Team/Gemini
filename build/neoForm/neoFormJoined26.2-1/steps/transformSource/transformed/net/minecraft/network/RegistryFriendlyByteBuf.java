package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;

public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    private final RegistryAccess registryAccess;
    private final net.neoforged.neoforge.network.connection.ConnectionType connectionType;

    /**
     * @deprecated Neo: use overload with ConnectionType context
     */
    @Deprecated
    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess) {
        super(source);
        this.registryAccess = registryAccess;
        this.connectionType = net.neoforged.neoforge.network.connection.ConnectionType.OTHER;
    }

    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        super(source);
        this.registryAccess = registryAccess;
        this.connectionType = connectionType;
    }

    public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
        return this.connectionType;
    }

    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess registryAccess, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        return buf -> new RegistryFriendlyByteBuf(buf, registryAccess, connectionType);
    }

    /**
     * @deprecated Neo: use overload with ConnectionType context
     */
    @Deprecated
    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess registryAccess) {
        return buf -> new RegistryFriendlyByteBuf(buf, registryAccess);
    }
}
