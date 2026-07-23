package net.minecraft.network.protocol.common;

import net.minecraft.network.protocol.cookie.ServerCookiePacketListener;

import net.neoforged.neoforge.common.extensions.IServerCommonPacketListenerExtension;

public interface ServerCommonPacketListener extends ServerCookiePacketListener, IServerCommonPacketListenerExtension {
    void handleKeepAlive(ServerboundKeepAlivePacket packet);

    void handlePong(ServerboundPongPacket serverboundPongPacket);

    void handleCustomPayload(ServerboundCustomPayloadPacket packet);

    void handleResourcePackResponse(ServerboundResourcePackPacket packet);

    void handleClientInformation(ServerboundClientInformationPacket packet);

    void handleCustomClickAction(ServerboundCustomClickActionPacket packet);
}
