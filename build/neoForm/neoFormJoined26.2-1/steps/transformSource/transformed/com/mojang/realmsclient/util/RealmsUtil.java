package com.mojang.realmsclient.util;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component RIGHT_NOW = Component.translatable("mco.util.time.now");
    private static final int MINUTES = 60;
    private static final int HOURS = 3600;
    private static final int DAYS = 86400;

    public static Component convertToAgePresentation(long timeDiff) {
        if (timeDiff < 0L) {
            return RIGHT_NOW;
        } else {
            long timeDiffInSeconds = timeDiff / 1000L;
            if (timeDiffInSeconds < 60L) {
                return Component.translatable("mco.time.secondsAgo", timeDiffInSeconds);
            } else if (timeDiffInSeconds < 3600L) {
                long minutes = timeDiffInSeconds / 60L;
                return Component.translatable("mco.time.minutesAgo", minutes);
            } else if (timeDiffInSeconds < 86400L) {
                long hours = timeDiffInSeconds / 3600L;
                return Component.translatable("mco.time.hoursAgo", hours);
            } else {
                long days = timeDiffInSeconds / 86400L;
                return Component.translatable("mco.time.daysAgo", days);
            }
        }
    }

    public static Component convertToAgePresentationFromInstant(Instant date) {
        return convertToAgePresentation(System.currentTimeMillis() - date.toEpochMilli());
    }

    public static void extractPlayerFace(GuiGraphicsExtractor graphics, int x, int y, int size, UUID playerId) {
        PlayerFaceExtractor.extractRenderState(graphics, ResolvableProfile.createUnresolved(playerId), x, y, size);
    }

    public static <T> CompletableFuture<T> supplyAsync(RealmsUtil.RealmsIoFunction<T> function, @Nullable Consumer<RealmsServiceException> onFailure) {
        return CompletableFuture.supplyAsync(() -> {
            RealmsClient client = RealmsClient.getOrCreate();

            try {
                return function.apply(client);
            } catch (Throwable t) {
                if (t instanceof RealmsServiceException e) {
                    if (onFailure != null) {
                        onFailure.accept(e);
                    }
                } else {
                    LOGGER.error("Unhandled exception", t);
                }

                throw new RuntimeException(t);
            }
        }, Util.nonCriticalIoPool());
    }

    public static CompletableFuture<Void> runAsync(RealmsUtil.RealmsIoConsumer function, @Nullable Consumer<RealmsServiceException> onFailure) {
        return supplyAsync(function, onFailure);
    }

    public static Consumer<RealmsServiceException> openScreenOnFailure(Function<RealmsServiceException, Screen> errorScreen) {
        Minecraft minecraft = Minecraft.getInstance();
        return e -> minecraft.execute(() -> minecraft.gui.setScreen(errorScreen.apply(e)));
    }

    public static Consumer<RealmsServiceException> openScreenAndLogOnFailure(Function<RealmsServiceException, Screen> errorScreen, String errorMessage) {
        return openScreenOnFailure(errorScreen).andThen(e -> LOGGER.error(errorMessage, e));
    }

    @FunctionalInterface
    public interface RealmsIoConsumer extends RealmsUtil.RealmsIoFunction<Void> {
        void accept(final RealmsClient client) throws RealmsServiceException;

        default Void apply(RealmsClient client) throws RealmsServiceException {
            this.accept(client);
            return null;
        }
    }

    @FunctionalInterface
    public interface RealmsIoFunction<T> {
        T apply(final RealmsClient client) throws RealmsServiceException;
    }
}
