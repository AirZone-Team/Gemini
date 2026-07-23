package net.minecraft.stats;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.player.Player;

public class StatsCounter {
    protected final Object2IntMap<Stat<?>> stats = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());

    public StatsCounter() {
        this.stats.defaultReturnValue(0);
    }

    public void increment(Player player, Stat<?> stat, int count) {
        int result = (int)Math.min((long)this.getValue(stat) + count, 2147483647L);
        this.setValue(player, stat, result);
    }

    public void setValue(Player player, Stat<?> stat, int count) {
        net.neoforged.neoforge.event.StatAwardEvent event = net.neoforged.neoforge.event.EventHooks.onStatAward(player, stat,count);
        if (!event.isCanceled()) this.stats.put(event.getStat(), event.getValue());
    }

    public <T> int getValue(StatType<T> type, T key) {
        return type.contains(key) ? this.getValue(type.get(key)) : 0;
    }

    public int getValue(Stat<?> stat) {
        return this.stats.getInt(stat);
    }
}
