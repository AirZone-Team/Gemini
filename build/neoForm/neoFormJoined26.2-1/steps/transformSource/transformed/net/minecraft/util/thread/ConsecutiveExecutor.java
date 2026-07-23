package net.minecraft.util.thread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public class ConsecutiveExecutor extends AbstractConsecutiveExecutor<Runnable> {
    public ConsecutiveExecutor(Executor dispatcher, String name) {
        super(new StrictQueue.QueueStrictQueue(new ConcurrentLinkedQueue<>()), dispatcher, name);
    }

    @Override
    public Runnable wrapRunnable(Runnable runnable) {
        return runnable;
    }
}
