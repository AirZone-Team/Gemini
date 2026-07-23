package net.minecraft.server.jsonrpc.methods;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.server.jsonrpc.api.PlayerDto;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.util.Util;

public class AllowlistService {
    public static List<PlayerDto> get(MinecraftApi minecraftApi) {
        return minecraftApi.allowListService().getEntries().stream().filter(p -> p.getUser() != null).map(u -> PlayerDto.from(u.getUser())).toList();
    }

    public static List<PlayerDto> add(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> fetch = playerDtos.stream()
            .map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> user : Util.sequence(fetch).join()) {
            user.ifPresent(nameAndId -> minecraftApi.allowListService().add(new UserWhiteListEntry(nameAndId), clientInfo));
        }

        return get(minecraftApi);
    }

    public static List<PlayerDto> clear(MinecraftApi minecraftApi, ClientInfo clientInfo) {
        minecraftApi.allowListService().clear(clientInfo);
        return get(minecraftApi);
    }

    public static List<PlayerDto> remove(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> fetch = playerDtos.stream()
            .map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();

        for (Optional<NameAndId> user : Util.sequence(fetch).join()) {
            user.ifPresent(nameAndId -> minecraftApi.allowListService().remove(nameAndId, clientInfo));
        }

        minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
        return get(minecraftApi);
    }

    public static List<PlayerDto> set(MinecraftApi minecraftApi, List<PlayerDto> playerDtos, ClientInfo clientInfo) {
        List<CompletableFuture<Optional<NameAndId>>> fetch = playerDtos.stream()
            .map(playerDto -> minecraftApi.playerListService().getUser(playerDto.id(), playerDto.name()))
            .toList();
        Set<NameAndId> finalAllowList = Util.sequence(fetch).join().stream().flatMap(Optional::stream).collect(Collectors.toSet());
        Set<NameAndId> currentAllowList = minecraftApi.allowListService().getEntries().stream().map(StoredUserEntry::getUser).collect(Collectors.toSet());
        currentAllowList.stream().filter(user -> !finalAllowList.contains(user)).forEach(user -> minecraftApi.allowListService().remove(user, clientInfo));
        finalAllowList.stream()
            .filter(user -> !currentAllowList.contains(user))
            .forEach(user -> minecraftApi.allowListService().add(new UserWhiteListEntry(user), clientInfo));
        minecraftApi.allowListService().kickUnlistedPlayers(clientInfo);
        return get(minecraftApi);
    }
}
