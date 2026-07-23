package net.minecraft.network.protocol.configuration;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;

import net.neoforged.neoforge.common.extensions.IServerConfigurationPacketListenerExtension;

public interface ServerConfigurationPacketListener extends ServerCommonPacketListener, IServerConfigurationPacketListenerExtension {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.CONFIGURATION;
    }

    void handleConfigurationFinished(ServerboundFinishConfigurationPacket packet);

    void handleSelectKnownPacks(ServerboundSelectKnownPacks packet);

    void handleAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket packet);
}
