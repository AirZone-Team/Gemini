package net.minecraft.server.network.config;

import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.TagNetworkSerialization;

public class SynchronizeRegistriesTask implements ConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("synchronize_registries");
    private final List<KnownPack> requestedPacks;
    private final LayeredRegistryAccess<RegistryLayer> registries;

    public SynchronizeRegistriesTask(List<KnownPack> knownPacks, LayeredRegistryAccess<RegistryLayer> registries) {
        this.requestedPacks = knownPacks;
        this.registries = registries;
    }

    @Override
    public void start(Consumer<Packet<?>> connection) {
        connection.accept(new ClientboundSelectKnownPacks(this.requestedPacks));
    }

    private void sendRegistries(Consumer<Packet<?>> connection, Set<KnownPack> negotiatedPacks) {
        DynamicOps<Tag> ops = this.registries.compositeAccess().createSerializationContext(NbtOps.INSTANCE);
        RegistrySynchronization.packRegistries(
            ops,
            this.registries.getAccessFrom(RegistryLayer.WORLDGEN),
            negotiatedPacks,
            (registryKey, entries) -> connection.accept(new ClientboundRegistryDataPacket(registryKey, entries))
        );
        connection.accept(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
    }

    public void handleResponse(List<KnownPack> acceptedPacks, Consumer<Packet<?>> connection) {
        // Neo: instead of using either all available KnownPacks or none, allow partial fallback to normal syncing
        Set<KnownPack> requested = new java.util.HashSet<>(this.requestedPacks);
        requested.retainAll(acceptedPacks);
        this.sendRegistries(connection, requested);
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}
