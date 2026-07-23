package net.minecraft.world.clock;

import net.minecraft.core.Holder;

public interface ClockManager {
    long getTotalTicks(Holder<WorldClock> definition);

    default float getPartialTick(Holder<WorldClock> definition) {
        return 0;
    }

    default void setRate(Holder<WorldClock> definition, float rate) {
    }
}
