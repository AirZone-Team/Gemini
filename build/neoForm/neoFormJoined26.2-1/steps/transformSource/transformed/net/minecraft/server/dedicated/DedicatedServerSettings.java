package net.minecraft.server.dedicated;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class DedicatedServerSettings {
    private final Path source;
    private DedicatedServerProperties properties;

    public DedicatedServerSettings(Path source) {
        this.source = source;
        this.properties = DedicatedServerProperties.fromFile(source);
    }

    public DedicatedServerProperties getProperties() {
        return this.properties;
    }

    public void forceSave() {
        this.properties.store(this.source);
    }

    public DedicatedServerSettings update(UnaryOperator<DedicatedServerProperties> mutator) {
        (this.properties = mutator.apply(this.properties)).store(this.source);
        return this;
    }
}
