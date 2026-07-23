package net.minecraft.world.level.gamerules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public record GameRuleCategory(Identifier id) {
    private static final List<GameRuleCategory> SORT_ORDER = new ArrayList<>();
    public static final GameRuleCategory PLAYER = register("player");
    public static final GameRuleCategory MOBS = register("mobs");
    public static final GameRuleCategory SPAWNING = register("spawning");
    public static final GameRuleCategory DROPS = register("drops");
    public static final GameRuleCategory UPDATES = register("updates");
    public static final GameRuleCategory CHAT = register("chat");
    public static final GameRuleCategory MISC = register("misc");

    public Identifier getDescriptionId() {
        return this.id;
    }

    private static GameRuleCategory register(String name) {
        return register(Identifier.withDefaultNamespace(name));
    }

    /**
     * @deprecated Prefer registering using {@link net.neoforged.neoforge.event.RegisterGameRuleCategoryEvent}
     */
    public static GameRuleCategory register(Identifier id) {
        GameRuleCategory category = new GameRuleCategory(id);
        if (SORT_ORDER.contains(category)) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Category '%s' is already registered.", id));
        }

        SORT_ORDER.add(category);
        return category;
    }

    public MutableComponent label() {
        return Component.translatable(this.id.toLanguageKey("gamerule.category"));
    }

    // Neo: Allow custom categories to be registered safely
    @org.jetbrains.annotations.ApiStatus.Internal
    public static void registerModdedCategories() {
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.event.RegisterGameRuleCategoryEvent(category -> {
            if (SORT_ORDER.contains(category)) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "Category '%s' is already registered.", category.id));
            } else {
                SORT_ORDER.add(category);
            }
        }));
    }
}
