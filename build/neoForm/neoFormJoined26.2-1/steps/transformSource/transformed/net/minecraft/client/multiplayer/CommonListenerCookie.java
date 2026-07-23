package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public record CommonListenerCookie(
    LevelLoadTracker levelLoadTracker,
    GameProfile localGameProfile,
    WorldSessionTelemetryManager telemetryManager,
    RegistryAccess.Frozen receivedRegistries,
    FeatureFlagSet enabledFeatures,
    @Nullable String serverBrand,
    @Nullable ServerData serverData,
    @Nullable Screen postDisconnectScreen,
    Map<Identifier, byte[]> serverCookies,
    ChatComponent.@Nullable State chatState,
    Map<String, String> customReportDetails,
    ServerLinks serverLinks,
    Map<UUID, PlayerInfo> seenPlayers,
    boolean seenInsecureChatWarning,
    net.neoforged.neoforge.network.connection.ConnectionType connectionType
) {
    /**
     * @deprecated Use {@link #CommonListenerCookie(LevelLoadTracker, GameProfile, WorldSessionTelemetryManager, RegistryAccess.Frozen, FeatureFlagSet, String, ServerData, Screen, Map, ChatComponent.State, Map, ServerLinks, Map, boolean, net.neoforged.neoforge.network.connection.ConnectionType)}
     * instead,to indicate whether the connection is modded.
     */
    @Deprecated
    public CommonListenerCookie(
            LevelLoadTracker levelLoadTracker,
            GameProfile localGameProfile,
            WorldSessionTelemetryManager telemetryManager,
            RegistryAccess.Frozen receivedRegistries,
            FeatureFlagSet enabledFeatures,
            @Nullable String serverBrand,
            @Nullable ServerData serverData,
            @Nullable Screen postDisconnectScreen,
            Map<Identifier, byte[]> serverCookies,
            ChatComponent.@Nullable State chatState,
            Map<String, String> customReportDetails,
            ServerLinks serverLinks,
            Map<UUID, PlayerInfo> seenPlayers,
            boolean seenInsecureChatWarning
    ) {
        this(levelLoadTracker, localGameProfile, telemetryManager, receivedRegistries, enabledFeatures, serverBrand, serverData, postDisconnectScreen, serverCookies, chatState, customReportDetails, serverLinks, seenPlayers, seenInsecureChatWarning, net.neoforged.neoforge.network.connection.ConnectionType.OTHER);
    }
}
