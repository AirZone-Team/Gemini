package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Unit;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

public class ReloadableServerResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final ReloadableServerRegistries.Holder fullRegistryHolder;
    private final Commands commands;
    private final RecipeManager recipes;
    private final ServerAdvancementManager advancements;
    private final ServerFunctionLibrary functionLibrary;
    private final List<Registry.PendingTags<?>> postponedTags;
    private final List<DataComponentInitializers.PendingComponents<?>> newComponents;
    // Neo: Store registries and condition context object
    private final net.minecraft.core.RegistryAccess registryAccess;
    private final HolderLookup.Provider loadingContext;
    private final net.neoforged.neoforge.common.conditions.ConditionContext context;
    private final java.util.Map<net.neoforged.neoforge.resource.ListenerKey<?>, PreparableReloadListener> retainedListeners = new java.util.IdentityHashMap<>();

    private ReloadableServerResources(
        LayeredRegistryAccess<RegistryLayer> fullLayers,
        HolderLookup.Provider loadingContext,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection commandSelection,
        List<Registry.PendingTags<?>> postponedTags,
        PermissionSet functionCompilationPermissions,
        List<DataComponentInitializers.PendingComponents<?>> newComponents
    ) {
        this.fullRegistryHolder = new ReloadableServerRegistries.Holder(fullLayers.compositeAccess());
        this.postponedTags = postponedTags;
        this.newComponents = newComponents;
        this.recipes = new RecipeManager(loadingContext);
        this.commands = new Commands(commandSelection, CommandBuildContext.simple(loadingContext, enabledFeatures));
        this.advancements = new ServerAdvancementManager(loadingContext);
        this.functionLibrary = new ServerFunctionLibrary(functionCompilationPermissions, this.commands.getDispatcher());
        // Neo: Store registries and create context object
        this.registryAccess = fullLayers.compositeAccess();
        this.loadingContext = loadingContext;
        this.context = new net.neoforged.neoforge.common.conditions.ConditionContext(this.postponedTags, registryAccess, enabledFeatures);
    }

    public ServerFunctionLibrary getFunctionLibrary() {
        return this.functionLibrary;
    }

    public ReloadableServerRegistries.Holder fullRegistries() {
        return this.fullRegistryHolder;
    }

    public RecipeManager getRecipeManager() {
        return this.recipes;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public ServerAdvancementManager getAdvancements() {
        return this.advancements;
    }

    public List<PreparableReloadListener> listeners() {
        return List.of(this.recipes, this.functionLibrary, this.advancements);
    }

    /// {@return the reload listener registered with the provided key}
    @SuppressWarnings("unchecked")
    public <T extends PreparableReloadListener> T getListener(net.neoforged.neoforge.resource.ListenerKey<T> key) {
        PreparableReloadListener listener = this.retainedListeners.get(key);
        if (listener == null) {
            throw new IllegalArgumentException("No listener registered for key " + key);
        }
        return (T) listener;
    }

    /**
     * Exposes the current condition context for usage in other reload listeners.<br>
     * This is not useful outside the reloading stage.
     * @return The condition context for the currently active reload.
     */
    public net.neoforged.neoforge.common.conditions.ICondition.IContext getConditionContext() {
        return this.context;
    }

    /**
      * {@return the lookup provider access for the currently active reload}
      */
    public HolderLookup.Provider getRegistryLookup() {
        return this.loadingContext;
    }

    public static CompletableFuture<ReloadableServerResources> loadResources(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> contextLayers,
        List<Registry.PendingTags<?>> updatedContextTags,
        FeatureFlagSet enabledFeatures,
        Commands.CommandSelection commandSelection,
        PermissionSet functionCompilationPermissions,
        Executor backgroundExecutor,
        Executor mainThreadExecutor
    ) {
        return ReloadableServerRegistries.reload(contextLayers, updatedContextTags, resourceManager, backgroundExecutor)
            .thenCompose(
                fullRegistries -> CompletableFuture.<List<DataComponentInitializers.PendingComponents<?>>>supplyAsync(
                        () -> BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(fullRegistries.lookupWithUpdatedTags()), backgroundExecutor
                    )
                    .thenCompose(
                        pendingComponents -> {
                            ReloadableServerResources result = new ReloadableServerResources(
                                fullRegistries.layers(),
                                fullRegistries.lookupWithUpdatedTags(),
                                enabledFeatures,
                                commandSelection,
                                updatedContextTags,
                                functionCompilationPermissions,
                                (List<DataComponentInitializers.PendingComponents<?>>)pendingComponents
                            );

                            // Neo: Fire the AddServerReloadListenersEvent and use the resulting listeners instead of the vanilla listener list.
                            List<PreparableReloadListener> listeners = net.neoforged.neoforge.event.EventHooks.onResourceReload(result, fullRegistries.layers().compositeAccess(), result.retainedListeners);

                            // Neo: Inject the ConditionContext and RegistryLookup to any resource listener that requests it.
                            for (PreparableReloadListener rl : listeners) {
                                if (rl instanceof net.neoforged.neoforge.resource.ContextAwareReloadListener carl) {
                                    carl.injectContext(result.context, result.loadingContext);
                                }
                            }

                            return SimpleReloadInstance.create(
                                    resourceManager,
                                    listeners,
                                    backgroundExecutor,
                                    mainThreadExecutor,
                                    DATA_RELOAD_INITIAL_TASK,
                                    LOGGER.isDebugEnabled()
                                )
                                .done()
                                .thenRun(() -> {
                                    // Neo: Clear context after reload completes
                                    result.context.clear();
                                    listeners.forEach(rl -> {
                                        if (rl instanceof net.neoforged.neoforge.resource.ContextAwareReloadListener srl) {
                                            srl.injectContext(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY, net.minecraft.core.RegistryAccess.EMPTY);
                                        }
                                    });
                                })
                                .thenApply(ignore -> result);
                        }
                    )
            );
    }

    public void updateComponentsAndStaticRegistryTags() {
        this.postponedTags.forEach(Registry.PendingTags::apply);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad(this, this.registryAccess));
        this.newComponents.forEach(DataComponentInitializers.PendingComponents::apply);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent(false, false));
    }
}
