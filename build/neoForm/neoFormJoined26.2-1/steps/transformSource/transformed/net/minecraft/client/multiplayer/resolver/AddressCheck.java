package net.minecraft.client.multiplayer.resolver;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.mojang.blocklist.BlockListSupplier;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface AddressCheck {
    boolean isAllowed(ResolvedServerAddress address);

    boolean isAllowed(ServerAddress address);

    static AddressCheck createFromService() {
        final ImmutableList<Predicate<String>> blockLists = Streams.stream(ServiceLoader.load(BlockListSupplier.class, net.neoforged.fml.loading.FMLLoader.class.getClassLoader()))
            .map(BlockListSupplier::createBlockList)
            .filter(Objects::nonNull)
            .collect(ImmutableList.toImmutableList());
        return new AddressCheck() {
            @Override
            public boolean isAllowed(ResolvedServerAddress address) {
                String hostName = address.getHostName();
                String hostIp = address.getHostIp();
                return blockLists.stream().noneMatch(p -> p.test(hostName) || p.test(hostIp));
            }

            @Override
            public boolean isAllowed(ServerAddress address) {
                String hostName = address.getHost();
                return blockLists.stream().noneMatch(p -> p.test(hostName));
            }
        };
    }
}
