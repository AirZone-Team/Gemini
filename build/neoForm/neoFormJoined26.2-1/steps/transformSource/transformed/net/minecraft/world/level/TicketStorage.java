package net.minecraft.world.level;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class TicketStorage extends SavedData {
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Pair<ChunkPos, Ticket>> TICKET_ENTRY = Codec.mapPair(ChunkPos.CODEC.fieldOf("chunk_pos"), Ticket.CODEC).codec();
    public static final Codec<TicketStorage> CODEC = RecordCodecBuilder.create(
        i -> i.group(TICKET_ENTRY.listOf().optionalFieldOf("tickets", List.of()).forGetter(TicketStorage::packTickets))
                .and(net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.defineExtraStorageParams())
                .apply(i, (tickets, neoData) -> net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.readStoredTickets(TicketStorage::fromPacked, tickets, neoData))
    );
    public static final SavedDataType<TicketStorage> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("chunk_tickets"), TicketStorage::new, CODEC, DataFixTypes.SAVED_DATA_FORCED_CHUNKS
    );
    private final Long2ObjectOpenHashMap<List<Ticket>> tickets;
    private final Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets;
    private LongSet chunksWithForcedTickets = new LongOpenHashSet();
    private TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener;
    private TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener;

    private TicketStorage(Long2ObjectOpenHashMap<List<Ticket>> tickets, Long2ObjectOpenHashMap<List<Ticket>> deactivatedTickets) {
        this.tickets = tickets;
        this.deactivatedTickets = deactivatedTickets;
        this.updateForcedChunks();
        this.updateForcedNaturalSpawning();
    }

    public TicketStorage() {
        this(new Long2ObjectOpenHashMap<>(4), new Long2ObjectOpenHashMap<>());
    }

    private static TicketStorage fromPacked(List<Pair<ChunkPos, Ticket>> tickets) {
        Long2ObjectOpenHashMap<List<Ticket>> ticketsToLoad = new Long2ObjectOpenHashMap<>();

        for (Pair<ChunkPos, Ticket> ticket : tickets) {
            ChunkPos pos = ticket.getFirst();
            List<Ticket> ticketsInChunk = ticketsToLoad.computeIfAbsent(pos.pack(), k -> new ObjectArrayList<>(4));
            ticketsInChunk.add(ticket.getSecond());
        }

        return new TicketStorage(new Long2ObjectOpenHashMap<>(4), ticketsToLoad);
    }

    private List<Pair<ChunkPos, Ticket>> packTickets() {
        List<Pair<ChunkPos, Ticket>> tickets = new ArrayList<>();
        this.forEachTicket((pos, ticket) -> {
            if (ticket.getType().persist()) {
                tickets.add(new Pair<>(pos, ticket));
            }
        });
        return tickets;
    }

    private void forEachTicket(BiConsumer<ChunkPos, Ticket> output) {
        forEachTicket(output, this.tickets);
        forEachTicket(output, this.deactivatedTickets);
    }

    private static void forEachTicket(BiConsumer<ChunkPos, Ticket> output, Long2ObjectOpenHashMap<List<Ticket>> tickets) {
        for (Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(tickets)) {
            ChunkPos chunkPos = ChunkPos.unpack(entry.getLongKey());

            for (Ticket ticket : entry.getValue()) {
                output.accept(chunkPos, ticket);
            }
        }
    }

    public void activateAllDeactivatedTickets() {
        for (Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(this.deactivatedTickets)) {
            for (Ticket ticket : entry.getValue()) {
                this.addTicket(entry.getLongKey(), ticket);
            }
        }

        this.deactivatedTickets.clear();
    }

    public void setLoadingChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated loadingChunkUpdatedListener) {
        this.loadingChunkUpdatedListener = loadingChunkUpdatedListener;
    }

    public void setSimulationChunkUpdatedListener(TicketStorage.@Nullable ChunkUpdated simulationChunkUpdatedListener) {
        this.simulationChunkUpdatedListener = simulationChunkUpdatedListener;
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    public boolean shouldKeepDimensionActive() {
        for (List<Ticket> group : this.tickets.values()) {
            for (Ticket ticket : group) {
                if (ticket.getType().shouldKeepDimensionActive()) {
                    return true;
                }
            }
        }

        return false;
    }

    public List<Ticket> getTickets(long key) {
        return this.tickets.getOrDefault(key, List.of());
    }

    private List<Ticket> getOrCreateTickets(long key) {
        return this.tickets.computeIfAbsent(key, k -> new ObjectArrayList<>(4));
    }

    public void addTicketWithRadius(TicketType type, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);
        this.addTicket(chunkPos.pack(), ticket);
    }

    public void addTicket(Ticket ticket, ChunkPos chunkPos) {
        this.addTicket(chunkPos.pack(), ticket);
    }

    public boolean addTicket(long key, Ticket ticket) {
        List<Ticket> tickets = this.getOrCreateTickets(key);

        for (Ticket t : tickets) {
            if (isTicketSameTypeAndLevel(ticket, t)) {
                t.resetTicksLeft();
                this.setDirty();
                return false;
            }
        }

        int oldSimulationTicketLevel = getTicketLevelAt(tickets, true);
        int oldLoadingTicketLevel = getTicketLevelAt(tickets, false);
        tickets.add(ticket);
        if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
            LOGGER.debug("ATI {} {}", ChunkPos.unpack(key), ticket);
        }

        if (ticket.getType().doesSimulate() && ticket.getTicketLevel() < oldSimulationTicketLevel && this.simulationChunkUpdatedListener != null) {
            this.simulationChunkUpdatedListener.update(key, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().doesLoad() && ticket.getTicketLevel() < oldLoadingTicketLevel && this.loadingChunkUpdatedListener != null) {
            this.loadingChunkUpdatedListener.update(key, ticket.getTicketLevel(), true);
        }

        if (ticket.getType().equals(TicketType.FORCED)) {
            this.chunksWithForcedTickets.add(key);
        }
        if (ticket.getType().forceNaturalSpawning()) chunksWithForceNaturalSpawning.add(key);

        this.setDirty();
        return true;
    }

    private static boolean isTicketSameTypeAndLevel(Ticket ticket, Ticket t) {
        return t.getType() == ticket.getType() && t.getTicketLevel() == ticket.getTicketLevel();
    }

    public int getTicketLevelAt(long key, boolean simulation) {
        return getTicketLevelAt(this.getTickets(key), simulation);
    }

    private static int getTicketLevelAt(List<Ticket> tickets, boolean simulation) {
        Ticket lowestTicket = getLowestTicket(tickets, simulation);
        return lowestTicket == null ? ChunkLevel.MAX_LEVEL + 1 : lowestTicket.getTicketLevel();
    }

    private static @Nullable Ticket getLowestTicket(@Nullable List<Ticket> tickets, boolean simulation) {
        if (tickets == null) {
            return null;
        }

        Ticket t = null;

        for (Ticket ticket : tickets) {
            if (t == null || ticket.getTicketLevel() < t.getTicketLevel()) {
                if (simulation && ticket.getType().doesSimulate()) {
                    t = ticket;
                } else if (!simulation && ticket.getType().doesLoad()) {
                    t = ticket;
                }
            }
        }

        return t;
    }

    public void removeTicketWithRadius(TicketType type, ChunkPos chunkPos, int radius) {
        Ticket ticket = new Ticket(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - radius);
        this.removeTicket(chunkPos.pack(), ticket);
    }

    public void removeTicket(Ticket ticket, ChunkPos chunkPos) {
        this.removeTicket(chunkPos.pack(), ticket);
    }

    public boolean removeTicket(long key, Ticket ticket) {
        List<Ticket> tickets = this.tickets.get(key);
        if (tickets == null) {
            return false;
        }

        boolean found = false;
        Iterator<Ticket> iterator = tickets.iterator();

        while (iterator.hasNext()) {
            Ticket t = iterator.next();
            if (isTicketSameTypeAndLevel(ticket, t)) {
                iterator.remove();
                if (SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
                    LOGGER.debug("RTI {} {}", ChunkPos.unpack(key), t);
                }

                found = true;
                break;
            }
        }

        if (!found) {
            return false;
        }

        if (tickets.isEmpty()) {
            this.tickets.remove(key);
        }

        if (ticket.getType().doesSimulate() && this.simulationChunkUpdatedListener != null) {
            this.simulationChunkUpdatedListener.update(key, getTicketLevelAt(tickets, true), false);
        }

        if (ticket.getType().doesLoad() && this.loadingChunkUpdatedListener != null) {
            this.loadingChunkUpdatedListener.update(key, getTicketLevelAt(tickets, false), false);
        }

        if (ticket.getType().equals(TicketType.FORCED)) {
            this.updateForcedChunks();
        }
        if (ticket.getType().forceNaturalSpawning()) this.updateForcedNaturalSpawning();

        this.setDirty();
        return true;
    }

    private void updateForcedChunks() {
        this.chunksWithForcedTickets = this.getAllChunksWithTicketThat(t -> t.getType().equals(TicketType.FORCED));
    }

    public String getTicketDebugString(long key, boolean simulation) {
        List<Ticket> tickets = this.getTickets(key);
        Ticket lowestTicket = getLowestTicket(tickets, simulation);
        return lowestTicket == null ? "no_ticket" : lowestTicket.toString();
    }

    public void purgeStaleTickets(ChunkMap chunkMap) {
        this.removeTicketIf((ticket, chunkPos) -> {
            if (this.canTicketExpire(chunkMap, ticket, chunkPos)) {
                ticket.decreaseTicksLeft();
                return ticket.isTimedOut();
            } else {
                return false;
            }
        }, null);
        this.setDirty();
    }

    private boolean canTicketExpire(ChunkMap chunkMap, Ticket ticket, long chunkPos) {
        if (!ticket.getType().hasTimeout()) {
            return false;
        }

        if (ticket.getType().canExpireIfUnloaded()) {
            return true;
        }

        ChunkHolder updatingChunk = chunkMap.getUpdatingChunkIfPresent(chunkPos);
        return updatingChunk == null || updatingChunk.isReadyForSaving();
    }

    public void deactivateTicketsOnClosing() {
        this.removeTicketIf((ticket, chunkPos) -> ticket.getType() != TicketType.UNKNOWN, this.deactivatedTickets);
        blockForcedChunks.deactivateTicketsOnClosing();
        entityForcedChunks.deactivateTicketsOnClosing();
    }

    public void removeTicketIf(TicketStorage.TicketPredicate predicate, @Nullable Long2ObjectOpenHashMap<List<Ticket>> removedTickets) {
        ObjectIterator<Entry<List<Ticket>>> ticketsPerChunkIterator = this.tickets.long2ObjectEntrySet().fastIterator();
        boolean removedForced = false;
        boolean updateNaturalSpawning = false;

        while (ticketsPerChunkIterator.hasNext()) {
            Entry<List<Ticket>> entry = ticketsPerChunkIterator.next();
            Iterator<Ticket> chunkTicketsIterator = entry.getValue().iterator();
            long chunkPos = entry.getLongKey();
            boolean removedSimulation = false;
            boolean removedLoading = false;

            while (chunkTicketsIterator.hasNext()) {
                Ticket ticket = chunkTicketsIterator.next();
                if (predicate.test(ticket, chunkPos)) {
                    if (removedTickets != null) {
                        List<Ticket> tickets = removedTickets.computeIfAbsent(chunkPos, k -> new ObjectArrayList<>(entry.getValue().size()));
                        tickets.add(ticket);
                    }

                    chunkTicketsIterator.remove();
                    if (ticket.getType().doesLoad()) {
                        removedLoading = true;
                    }

                    if (ticket.getType().doesSimulate()) {
                        removedSimulation = true;
                    }

                    if (ticket.getType().equals(TicketType.FORCED)) {
                        removedForced = true;
                    }
                    if (ticket.getType().forceNaturalSpawning()) updateNaturalSpawning = true;
                }
            }

            if (removedLoading || removedSimulation) {
                if (removedLoading && this.loadingChunkUpdatedListener != null) {
                    this.loadingChunkUpdatedListener.update(chunkPos, getTicketLevelAt(entry.getValue(), false), false);
                }

                if (removedSimulation && this.simulationChunkUpdatedListener != null) {
                    this.simulationChunkUpdatedListener.update(chunkPos, getTicketLevelAt(entry.getValue(), true), false);
                }

                this.setDirty();
                if (entry.getValue().isEmpty()) {
                    ticketsPerChunkIterator.remove();
                }
            }
        }

        if (removedForced) {
            this.updateForcedChunks();
        }
        if (updateNaturalSpawning) this.updateForcedNaturalSpawning();
    }

    public void replaceTicketLevelOfType(int newLevel, TicketType ticketType) {
        List<Pair<Ticket, Long>> affectedTickets = new ArrayList<>();

        for (Entry<List<Ticket>> entry : this.tickets.long2ObjectEntrySet()) {
            for (Ticket ticket : entry.getValue()) {
                if (ticket.getType() == ticketType) {
                    affectedTickets.add(Pair.of(ticket, entry.getLongKey()));
                }
            }
        }

        for (Pair<Ticket, Long> pair : affectedTickets) {
            Long key = pair.getSecond();
            Ticket ticket = pair.getFirst();
            this.removeTicket(key, ticket);
            TicketType type = ticket.getType();
            this.addTicket(key, new Ticket(type, newLevel));
        }
    }

    public boolean updateChunkForced(ChunkPos chunkPos, boolean forced) {
        Ticket ticket = new Ticket(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL);
        return forced ? this.addTicket(chunkPos.pack(), ticket) : this.removeTicket(chunkPos.pack(), ticket);
    }

    public LongSet getForceLoadedChunks() {
        return this.chunksWithForcedTickets;
    }

    private LongSet getAllChunksWithTicketThat(Predicate<Ticket> ticketCheck) {
        LongOpenHashSet chunks = new LongOpenHashSet();

        for (Entry<List<Ticket>> entry : Long2ObjectMaps.fastIterable(this.tickets)) {
            for (Ticket ticket : entry.getValue()) {
                if (ticketCheck.test(ticket)) {
                    chunks.add(entry.getLongKey());
                    break;
                }
            }
        }

        return chunks;
    }

    @FunctionalInterface
    public interface ChunkUpdated {
        void update(final long node, final int newLevelFrom, final boolean onlyDecreased);
    }

    public interface TicketPredicate {
        boolean test(Ticket ticket, long chunkPos);
    }

    // Neo: Keep track of forced loaded chunks caused by entities or blocks.
    private final net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<net.minecraft.core.BlockPos> blockForcedChunks = new net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<>(this, net.neoforged.neoforge.common.NeoForgeMod.BLOCK_TICKET, net.neoforged.neoforge.common.NeoForgeMod.BLOCK_WITH_NATURAL_SPAWNING_TICKET);
    private final net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<java.util.UUID> entityForcedChunks = new net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<>(this, net.neoforged.neoforge.common.NeoForgeMod.ENTITY_TICKET, net.neoforged.neoforge.common.NeoForgeMod.ENTITY_WITH_NATURAL_SPAWNING_TICKET);
    private LongSet chunksWithForceNaturalSpawning = new LongOpenHashSet();

    public net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<net.minecraft.core.BlockPos> getBlockForcedChunks() {
        return this.blockForcedChunks;
    }

    public net.neoforged.neoforge.common.world.chunk.ForcedChunkManager.TicketTracker<java.util.UUID> getEntityForcedChunks() {
        return this.entityForcedChunks;
    }

    private void updateForcedNaturalSpawning() {
        this.chunksWithForceNaturalSpawning = this.getAllChunksWithTicketThat(ticket -> ticket.getType().forceNaturalSpawning());
    }

    public boolean shouldForceNaturalSpawning(ChunkPos chunkPos) {
        return chunksWithForceNaturalSpawning.contains(chunkPos.pack());
    }
}
