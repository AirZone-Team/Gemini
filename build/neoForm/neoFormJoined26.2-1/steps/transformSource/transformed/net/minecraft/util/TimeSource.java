package net.minecraft.util;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@FunctionalInterface
public interface TimeSource {
    long get(TimeUnit timeUnit);

    static TimeSource.NanoTimeSource constant(long value) {
        return () -> value;
    }

    interface NanoTimeSource extends LongSupplier, TimeSource {
        @Override
        default long get(TimeUnit timeUnit) {
            return timeUnit.convert(this.getAsLong(), TimeUnit.NANOSECONDS);
        }
    }
}
