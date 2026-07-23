package net.minecraft.client.multiplayer.resolver;

import com.google.common.net.HostAndPort;
import com.mojang.logging.LogUtils;
import java.net.IDN;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public final class ServerAddress {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final HostAndPort hostAndPort;
    private static final ServerAddress INVALID = new ServerAddress(HostAndPort.fromParts("server.invalid", 25565));

    public ServerAddress(String host, int port) {
        this(HostAndPort.fromParts(host, port));
    }

    private ServerAddress(HostAndPort hostAndPort) {
        this.hostAndPort = hostAndPort;
    }

    public String getHost() {
        try {
            return IDN.toASCII(this.hostAndPort.getHost());
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public int getPort() {
        return this.hostAndPort.getPort();
    }

    public static ServerAddress parseString(@Nullable String input) {
        if (input == null) {
            return INVALID;
        }

        try {
            HostAndPort result = HostAndPort.fromString(input).withDefaultPort(25565);
            return result.getHost().isEmpty() ? INVALID : new ServerAddress(result);
        } catch (IllegalArgumentException e) {
            LOGGER.info("Failed to parse URL {}", input, e);
            return INVALID;
        }
    }

    public static boolean isValidAddress(String input) {
        try {
            HostAndPort hostAndPort = HostAndPort.fromString(input);
            String host = hostAndPort.getHost();
            if (!host.isEmpty()) {
                IDN.toASCII(host);
                return true;
            }
        } catch (IllegalArgumentException var3) {
        }

        return false;
    }

    public static int parsePort(String str) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception var2) {
            return 25565;
        }
    }

    @Override
    public String toString() {
        return this.hostAndPort.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else {
            return o instanceof ServerAddress serverAddress ? this.hostAndPort.equals(serverAddress.hostAndPort) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.hostAndPort.hashCode();
    }
}
