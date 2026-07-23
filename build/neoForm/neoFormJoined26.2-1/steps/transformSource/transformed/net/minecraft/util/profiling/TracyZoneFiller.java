package net.minecraft.util.profiling;

import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.util.profiling.metrics.MetricCategory;
import org.slf4j.Logger;

public class TracyZoneFiller implements ProfilerFiller {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(Set.of(Option.RETAIN_CLASS_REFERENCE), 5);
    private final List<com.mojang.jtracy.Zone> activeZones = new ArrayList<>();
    private final Map<String, TracyZoneFiller.PlotAndValue> plots = new HashMap<>();
    private final String name = Thread.currentThread().getName();

    @Override
    public void startTick() {
    }

    @Override
    public void endTick() {
        for (TracyZoneFiller.PlotAndValue plotAndValue : this.plots.values()) {
            plotAndValue.set(0);
        }
    }

    @Override
    public void push(String name) {
        String function = "";
        String file = "";
        int line = 0;
        if (SharedConstants.IS_RUNNING_IN_IDE) {
            Optional<StackFrame> result = STACK_WALKER.walk(
                s -> s.filter(
                        framex -> framex.getDeclaringClass() != TracyZoneFiller.class
                            && framex.getDeclaringClass() != ProfilerFiller.CombinedProfileFiller.class
                    )
                    .findFirst()
            );
            if (result.isPresent()) {
                StackFrame frame = result.get();
                function = frame.getMethodName();
                file = frame.getFileName();
                line = frame.getLineNumber();
            }
        }

        com.mojang.jtracy.Zone zone = TracyClient.beginZone(name, function, file, line);
        this.activeZones.add(zone);
    }

    @Override
    public void push(Supplier<String> name) {
        this.push(name.get());
    }

    @Override
    public void pop() {
        if (this.activeZones.isEmpty()) {
            LOGGER.error("Tried to pop one too many times! Mismatched push() and pop()?");
        } else {
            com.mojang.jtracy.Zone zone = this.activeZones.removeLast();
            zone.close();
        }
    }

    @Override
    public void popPush(String name) {
        this.pop();
        this.push(name);
    }

    @Override
    public void popPush(Supplier<String> name) {
        this.pop();
        this.push(name.get());
    }

    @Override
    public void markForCharting(MetricCategory category) {
    }

    @Override
    public void incrementCounter(String name, int amount) {
        this.plots.computeIfAbsent(name, s -> new TracyZoneFiller.PlotAndValue(this.name + " " + name)).add(amount);
    }

    @Override
    public void incrementCounter(Supplier<String> name, int amount) {
        this.incrementCounter(name.get(), amount);
    }

    private com.mojang.jtracy.Zone activeZone() {
        return this.activeZones.getLast();
    }

    @Override
    public void addZoneText(String text) {
        this.activeZone().addText(text);
    }

    @Override
    public void addZoneValue(long value) {
        this.activeZone().addValue(value);
    }

    @Override
    public void setZoneColor(int color) {
        this.activeZone().setColor(color);
    }

    private static final class PlotAndValue {
        private final Plot plot;
        private int value;

        private PlotAndValue(String name) {
            this.plot = TracyClient.createPlot(name);
            this.value = 0;
        }

        public void set(int value) {
            this.value = value;
            this.plot.setValue(value);
        }

        public void add(int amount) {
            this.set(this.value + amount);
        }
    }
}
