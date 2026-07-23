package net.minecraft.server.network;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;

public interface ConfigurationTask {
    void start(Consumer<Packet<?>> connection);

    default boolean tick() {
        return false;
    }

    ConfigurationTask.Type type();

    record Type(String id) {
        public Type(net.minecraft.resources.Identifier location) {
            this(location.toString());
        }

        @Override
        public String toString() {
            return this.id;
        }
    }
}
