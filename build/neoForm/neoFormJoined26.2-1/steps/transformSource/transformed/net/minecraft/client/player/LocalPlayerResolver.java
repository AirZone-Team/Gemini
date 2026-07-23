package net.minecraft.client.player;

import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.server.players.ProfileResolver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LocalPlayerResolver implements ProfileResolver {
    private final Minecraft minecraft;
    private final ProfileResolver parentResolver;

    public LocalPlayerResolver(Minecraft minecraft, ProfileResolver parentResolver) {
        this.minecraft = minecraft;
        this.parentResolver = parentResolver;
    }

    @Override
    public Optional<GameProfile> fetchByName(String name) {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfoIgnoreCase(name);
            if (playerInfo != null) {
                return Optional.of(playerInfo.getProfile());
            }
        }

        return this.parentResolver.fetchByName(name);
    }

    @Override
    public Optional<GameProfile> fetchById(UUID id) {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(id);
            if (playerInfo != null) {
                return Optional.of(playerInfo.getProfile());
            }
        }

        return this.parentResolver.fetchById(id);
    }
}
