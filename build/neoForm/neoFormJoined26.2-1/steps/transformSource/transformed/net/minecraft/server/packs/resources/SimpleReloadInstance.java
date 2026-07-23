package net.minecraft.server.packs.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class SimpleReloadInstance<S> implements ReloadInstance {
    private static final int PREPARATION_PROGRESS_WEIGHT = 2;
    private static final int EXTRA_RELOAD_PROGRESS_WEIGHT = 2;
    private static final int LISTENER_PROGRESS_WEIGHT = 1;
    private final CompletableFuture<Unit> allPreparations = new CompletableFuture<>();
    private @Nullable CompletableFuture<List<S>> allDone;
    private final Set<PreparableReloadListener> preparingListeners;
    private final int listenerCount;
    private final AtomicInteger startedTasks = new AtomicInteger();
    private final AtomicInteger finishedTasks = new AtomicInteger();
    private final AtomicInteger startedReloads = new AtomicInteger();
    private final AtomicInteger finishedReloads = new AtomicInteger();

    public static ReloadInstance of(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor taskExecutor,
        Executor mainThreadExecutor,
        CompletableFuture<Unit> initialTask
    ) {
        SimpleReloadInstance<Void> result = new SimpleReloadInstance<>(listeners);
        result.startTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, SimpleReloadInstance.StateFactory.SIMPLE, initialTask);
        return result;
    }

    protected SimpleReloadInstance(List<PreparableReloadListener> listeners) {
        this.listenerCount = listeners.size();
        this.preparingListeners = new HashSet<>(listeners);
    }

    protected void startTasks(
        Executor taskExecutor,
        Executor mainThreadExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<?> initialTask
    ) {
        this.allDone = this.prepareTasks(taskExecutor, mainThreadExecutor, resourceManager, listeners, stateFactory, initialTask);
    }

    protected CompletableFuture<List<S>> prepareTasks(
        Executor taskExecutor,
        Executor mainThreadExecutor,
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        SimpleReloadInstance.StateFactory<S> stateFactory,
        CompletableFuture<?> initialTask
    ) {
        Executor countingTaskExecutor = r -> {
            this.startedTasks.incrementAndGet();
            taskExecutor.execute(() -> {
                r.run();
                this.finishedTasks.incrementAndGet();
            });
        };
        Executor countingReloadExecutor = r -> {
            this.startedReloads.incrementAndGet();
            mainThreadExecutor.execute(() -> {
                r.run();
                this.finishedReloads.incrementAndGet();
            });
        };
        this.startedTasks.incrementAndGet();
        initialTask.thenRun(this.finishedTasks::incrementAndGet);
        PreparableReloadListener.SharedState sharedState = new PreparableReloadListener.SharedState(resourceManager);
        listeners.forEach(listenerx -> listenerx.prepareSharedState(sharedState));
        CompletableFuture<?> barrier = initialTask;
        List<CompletableFuture<S>> allSteps = new ArrayList<>();

        for (PreparableReloadListener listener : listeners) {
            PreparableReloadListener.PreparationBarrier barrierForCurrentTask = this.createBarrierForListener(listener, barrier, mainThreadExecutor);
            CompletableFuture<S> state = stateFactory.create(sharedState, barrierForCurrentTask, listener, countingTaskExecutor, countingReloadExecutor);
            allSteps.add(state);
            barrier = state;
        }

        return Util.sequenceFailFast(allSteps);
    }

    private PreparableReloadListener.PreparationBarrier createBarrierForListener(
        PreparableReloadListener listener, CompletableFuture<?> previousBarrier, Executor mainThreadExecutor
    ) {
        return new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T t) {
                mainThreadExecutor.execute(() -> {
                    SimpleReloadInstance.this.preparingListeners.remove(listener);
                    if (SimpleReloadInstance.this.preparingListeners.isEmpty()) {
                        SimpleReloadInstance.this.allPreparations.complete(Unit.INSTANCE);
                    }
                });
                return SimpleReloadInstance.this.allPreparations.thenCombine((CompletionStage<? extends T>)previousBarrier, (v1, v2) -> t);
            }
        };
    }

    @Override
    public CompletableFuture<?> done() {
        return Objects.requireNonNull(this.allDone, "not started");
    }

    @Override
    public float getActualProgress() {
        int preparationsDone = this.listenerCount - this.preparingListeners.size();
        float doneCount = weightProgress(this.finishedTasks.get(), this.finishedReloads.get(), preparationsDone);
        float totalCount = weightProgress(this.startedTasks.get(), this.startedReloads.get(), this.listenerCount);
        return doneCount / totalCount;
    }

    private static int weightProgress(int preparationTasks, int reloadTasks, int listeners) {
        return preparationTasks * 2 + reloadTasks * 2 + listeners * 1;
    }

    public static ReloadInstance create(
        ResourceManager resourceManager,
        List<PreparableReloadListener> listeners,
        Executor backgroundExecutor,
        Executor mainThreadExecutor,
        CompletableFuture<Unit> initialTask,
        boolean enableProfiling
    ) {
        return enableProfiling
            ? ProfiledReloadInstance.of(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask)
            : of(resourceManager, listeners, backgroundExecutor, mainThreadExecutor, initialTask);
    }

    @FunctionalInterface
    protected interface StateFactory<S> {
        SimpleReloadInstance.StateFactory<Void> SIMPLE = (currentReload, previousStep, listener, taskExecutor, reloadExecutor) -> listener.reload(
            currentReload, taskExecutor, previousStep, reloadExecutor
        );

        CompletableFuture<S> create(
            PreparableReloadListener.SharedState sharedState,
            PreparableReloadListener.PreparationBarrier previousStep,
            PreparableReloadListener listener,
            Executor taskExecutor,
            Executor reloadExecutor
        );
    }
}
