package net.minecraft.client.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.MonthDay;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpecialDates;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class SplashManager extends SimplePreparableReloadListener<List<Component>> {
    private static final Style DEFAULT_STYLE = Style.EMPTY.withColor(-256);
    public static final Component CHRISTMAS = literalSplash("Merry X-mas!");
    public static final Component NEW_YEAR = literalSplash("Happy new year!");
    public static final Component HALLOWEEN = literalSplash("OOoooOOOoooo! Spooky!");
    public static final Identifier SPLASHES_LOCATION = Identifier.withDefaultNamespace("texts/splashes.txt");
    private static final RandomSource RANDOM = RandomSource.create();
    private List<Component> splashes = List.of();
    private final User user;

    public SplashManager(User user) {
        this.user = user;
    }

    public static Component literalSplash(String text) {
        return Component.literal(text).setStyle(DEFAULT_STYLE);
    }

    protected List<Component> prepare(ResourceManager manager, ProfilerFiller profiler) {
        net.minecraft.server.packs.resources.Resource splashes;
        try {
            splashes = Minecraft.getInstance().getResourceManager().getResourceOrThrow(SPLASHES_LOCATION);
        } catch (IOException e) {
            return List.of();
        }
        if (splashes.sourcePackId().equals("vanilla") || !net.neoforged.neoforge.client.config.NeoForgeClientConfig.INSTANCE.skipModdedSplashesIfVanillaModified.getAsBoolean())
            return net.neoforged.neoforge.client.resources.NeoForgeSplashHooks.loadSplashes(manager);

        try (BufferedReader reader = splashes.openAsReader()) {
            return reader.lines().map(String::trim).filter(line -> line.hashCode() != 125780783).map(SplashManager::literalSplash).toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    protected void apply(List<Component> preparations, ResourceManager manager, ProfilerFiller profiler) {
        this.splashes = List.copyOf(preparations);
    }

    public @Nullable SplashRenderer getSplash() {
        MonthDay monthDay = SpecialDates.dayNow();
        if (monthDay.equals(SpecialDates.CHRISTMAS)) {
            return SplashRenderer.CHRISTMAS;
        } else if (monthDay.equals(SpecialDates.NEW_YEAR)) {
            return SplashRenderer.NEW_YEAR;
        } else if (monthDay.equals(SpecialDates.HALLOWEEN)) {
            return SplashRenderer.HALLOWEEN;
        } else if (this.splashes.isEmpty()) {
            return null;
        } else {
            return this.user != null && RANDOM.nextInt(this.splashes.size()) == 42
                ? new SplashRenderer(literalSplash(this.user.getName().toUpperCase(Locale.ROOT) + " IS YOU"))
                : new SplashRenderer(this.splashes.get(RANDOM.nextInt(this.splashes.size())));
        }
    }
}
