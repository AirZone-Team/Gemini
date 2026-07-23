package net.minecraft.client.server;

import java.net.SocketAddress;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IntegratedPlayerList extends PlayerList {
    public IntegratedPlayerList(IntegratedServer server, LayeredRegistryAccess<RegistryLayer> registryHolder, PlayerDataStorage playerDataStorage) {
        super(server, registryHolder, playerDataStorage, server.notificationManager());
        this.setViewDistance(10);
    }

    @Override
    public Component canPlayerLogin(SocketAddress address, NameAndId nameAndId) {
        return this.getServer().isSingleplayerOwner(nameAndId) && this.getPlayerByName(nameAndId.name()) != null
            ? Component.translatable("multiplayer.disconnect.name_taken")
            : super.canPlayerLogin(address, nameAndId);
    }

    public IntegratedServer getServer() {
        return (IntegratedServer)super.getServer();
    }
}
