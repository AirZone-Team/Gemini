package net.minecraft.world.clock;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class ServerClockManager extends SavedData implements ClockManager {
    public static final SavedDataType<ServerClockManager> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("world_clocks"),
        () -> new ServerClockManager(PackedClockStates.EMPTY),
        PackedClockStates.CODEC.xmap(ServerClockManager::new, ServerClockManager::packState),
        DataFixTypes.SAVED_DATA_WORLD_CLOCKS
    );
    private final PackedClockStates packedClockStates;
    private MinecraftServer server;
    private final Map<Holder<WorldClock>, ServerClockManager.ClockInstance> clocks = new HashMap<>();

    private ServerClockManager(PackedClockStates packedClockStates) {
        this.packedClockStates = packedClockStates;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        server.registryAccess()
            .lookupOrThrow(Registries.WORLD_CLOCK)
            .listElements()
            .forEach(definition -> this.clocks.put(definition, new ServerClockManager.ClockInstance(definition)));
        server.registryAccess()
            .lookupOrThrow(Registries.TIMELINE)
            .listElements()
            .forEach(timeline -> timeline.value().registerTimeMarkers(this::registerTimeMarker));
        this.packedClockStates.clocks().forEach((definition, state) -> {
            ServerClockManager.ClockInstance instance = this.getInstance((Holder<WorldClock>)definition);
            instance.loadFrom(state);
        });
    }

    private void registerTimeMarker(ResourceKey<ClockTimeMarker> timeMarkerId, ClockTimeMarker timeMarker) {
        this.getInstance(timeMarker.clock()).timeMarkers.put(timeMarkerId, timeMarker);
    }

    public PackedClockStates packState() {
        return new PackedClockStates(Util.mapValues(this.clocks, ServerClockManager.ClockInstance::packState));
    }

    public void tick() {
        if (this.server.overworld() == null) {
            return; // Neo: Our JUnit ephemeral testserver has no overworld and getGlobalGameRules() would crash
        }
        boolean advanceTime = this.server.getGlobalGameRules().get(GameRules.ADVANCE_TIME);
        // Neo: Custom ticking logic to move the advance time check to be per clock rather than global
        this.clocks.values().forEach(clock -> clock.tick(advanceTime));
        if (advanceTime) {
            this.setDirty();
        }
    }

    private ServerClockManager.ClockInstance getInstance(Holder<WorldClock> definition) {
        ServerClockManager.ClockInstance instance = this.clocks.get(definition);
        if (instance == null) {
            throw new IllegalStateException("No clock initialized for definition: " + definition);
        } else {
            return instance;
        }
    }

    public void setTotalTicks(Holder<WorldClock> clock, long totalTicks) {
        this.modifyClock(clock, instance -> {
            instance.totalTicks = totalTicks;
            instance.partialTick = 0.0F;
        });
    }

    public boolean moveToTimeMarker(Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId) {
        MutableBoolean set = new MutableBoolean();
        this.modifyClock(clock, instance -> {
            ClockTimeMarker timeMarker = instance.timeMarkers.get(timeMarkerId);
            if (timeMarker != null) {
                instance.totalTicks = timeMarker.resolveTimeToMoveTo(instance.totalTicks);
                instance.partialTick = 0.0F;
                set.setTrue();
            }
        });
        return set.booleanValue();
    }

    public void addTicks(Holder<WorldClock> clock, int ticks) {
        this.modifyClock(clock, instance -> instance.totalTicks = Math.max(instance.totalTicks + ticks, 0L));
    }

    public void setPaused(Holder<WorldClock> clock, boolean paused) {
        this.modifyClock(clock, instance -> instance.paused = paused);
    }

    /**
     * This allows mods to set the rate at which a specific clock advances relative to game time.
     * <p>
     * This can be sped up for shorter days by giving a higher number, or slowed down for longer days
     * with a smaller number. A negative value will reset it back to vanilla logic.
     * <p>
     * This value can also be changed with the command <code>/neoforge day</code>, where you can set
     * either the speed or a day length in minutes.
     * <p>
     * This has no effect when time progression is stopped.
     * <p>
     * While this still technically works when vanilla clients are connected, those will desync and
     * experience a time jump once per second.
     */
    @Override
    public void setRate(Holder<WorldClock> clock, float rate) {
        this.modifyClock(clock, instance -> instance.rate = rate);
    }

    private void modifyClock(Holder<WorldClock> clock, Consumer<? super ServerClockManager.ClockInstance> action) {
        ServerClockManager.ClockInstance instance = this.getInstance(clock);
        action.accept(instance);
        Map<Holder<WorldClock>, ClockNetworkState> updates = Map.of(clock, instance.packNetworkState(this.server));
        this.server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(this.getGameTime(), updates));
        this.setDirty();

        for (ServerLevel level : this.server.getAllLevels()) {
            level.environmentAttributes().invalidateTickCache();
        }
    }

    /**
     * Returns the current ratio between game ticks and clock ticks. This value cannot be 0 or negative.
     * The default is 1.
     */
    public float getRate(Holder<WorldClock> definition) {
        return getInstance(definition).rate;
    }

    @Override
    public long getTotalTicks(Holder<WorldClock> definition) {
        return this.getInstance(definition).totalTicks;
    }

    @Override
    public float getPartialTick(Holder<WorldClock> definition) {
        return this.getInstance(definition).partialTick;
    }

    public ClientboundSetTimePacket createFullSyncPacket() {
        return new ClientboundSetTimePacket(this.getGameTime(), Util.mapValues(this.clocks, clock -> clock.packNetworkState(this.server)));
    }

    private long getGameTime() {
        return this.server.overworld().getGameTime();
    }

    public boolean isAtTimeMarker(Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> timeMarkerId) {
        ServerClockManager.ClockInstance clockInstance = this.getInstance(clock);
        ClockTimeMarker timeMarker = clockInstance.timeMarkers.get(timeMarkerId);
        return timeMarker != null && timeMarker.occursAt(clockInstance.totalTicks);
    }

    public Stream<ResourceKey<ClockTimeMarker>> commandTimeMarkersForClock(Holder<WorldClock> clock) {
        return this.getInstance(clock).timeMarkers.entrySet().stream().filter(entry -> entry.getValue().showInCommands()).map(Entry::getKey);
    }

    private static class ClockInstance {
        private final Map<ResourceKey<ClockTimeMarker>, ClockTimeMarker> timeMarkers = new Reference2ObjectOpenHashMap<>();
        private long totalTicks;
        private float partialTick;
        private float rate = 1.0F;
        private boolean paused;
        private final @org.jspecify.annotations.Nullable Holder<WorldClock> holder;

        public ClockInstance(Holder<WorldClock> holder) {
            this.holder = holder;
        }

        /// @deprecated Neo: Use [#ClockInstance(Holder)] instead
        @Deprecated
        public ClockInstance() {
            holder = null;
        }

        public void loadFrom(ClockState state) {
            this.totalTicks = state.totalTicks();
            this.partialTick = state.partialTick();
            this.rate = state.rate();
            this.paused = state.paused();
        }

        /// @deprecated Neo: Use [#tick(boolean)], Passing in if time is advancing
        @Deprecated
        public void tick() {
            // Neo: Call the new method with the vanilla value
            // Vanilla calls this inside of 'GameRules.ADVANCE_TIME == true' check
            this.tick(true);
        }

        public void tick(boolean advanceTime) {
            if (this.ticking(advanceTime)) {
                this.partialTick = this.partialTick + this.rate;
                int fullTicks = Mth.floor(this.partialTick);
                this.partialTick -= fullTicks;
                this.totalTicks += fullTicks;
            }
        }

        public ClockState packState() {
            return new ClockState(this.totalTicks, this.partialTick, this.rate, this.paused);
        }

        public ClockNetworkState packNetworkState(MinecraftServer server) {
            boolean advanceTime = server.getGlobalGameRules().get(GameRules.ADVANCE_TIME);
            // Neo: Use the corrected pause value
            boolean paused = !this.ticking(advanceTime);
            return new ClockNetworkState(this.totalTicks, this.partialTick, paused ? 0.0F : this.rate);
        }

        private boolean ticking(boolean advanceTime) {
            if (this.paused && !this.hasTag(net.neoforged.neoforge.common.Tags.WorldClocks.IGNORES_PAUSE_COMMAND)) {
                return false;
            }

            return advanceTime || this.hasTag(net.neoforged.neoforge.common.Tags.WorldClocks.IGNORES_ADVANCE_TIME_RULE);
        }

        private boolean hasTag(net.minecraft.tags.TagKey<WorldClock> tag) {
            return this.holder != null && this.holder.is(tag);
        }
    }
}
