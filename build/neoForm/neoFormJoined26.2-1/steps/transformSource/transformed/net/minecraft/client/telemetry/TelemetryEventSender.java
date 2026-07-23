package net.minecraft.client.telemetry;

import java.util.function.Consumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface TelemetryEventSender {
    TelemetryEventSender DISABLED = (type, buildFunction) -> {};

    default TelemetryEventSender decorate(Consumer<TelemetryPropertyMap.Builder> decorator) {
        return (type, buildFunction) -> this.send(type, properties -> {
            buildFunction.accept(properties);
            decorator.accept(properties);
        });
    }

    void send(TelemetryEventType type, Consumer<TelemetryPropertyMap.Builder> buildFunction);
}
