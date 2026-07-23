package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> extends net.neoforged.neoforge.resource.ContextAwareReloadListener implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState currentReload,
        Executor taskExecutor,
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        Executor reloadExecutor
    ) {
        ResourceManager manager = currentReload.resourceManager();
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(manager, Profiler.get()), taskExecutor)
            .thenCompose(preparationBarrier::wait)
            .thenAcceptAsync(preparations -> this.apply((T)preparations, manager, Profiler.get()), reloadExecutor);
    }

    protected abstract T prepare(final ResourceManager manager, final ProfilerFiller profiler);

    protected abstract void apply(final T preparations, final ResourceManager manager, final ProfilerFiller profiler);
}
