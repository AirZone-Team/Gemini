package net.minecraft.world.level.chunk.status;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public class ChunkStatus {
    public static final int MAX_STRUCTURE_DISTANCE = 8;
    private static final EnumSet<Heightmap.Types> WORLDGEN_HEIGHTMAPS = EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
    public static final EnumSet<Heightmap.Types> FINAL_HEIGHTMAPS = EnumSet.of(
        Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
    );
    public static final ChunkStatus EMPTY = register("empty", null, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus STRUCTURE_STARTS = register("structure_starts", EMPTY, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus STRUCTURE_REFERENCES = register("structure_references", STRUCTURE_STARTS, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus BIOMES = register("biomes", STRUCTURE_REFERENCES, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus NOISE = register("noise", BIOMES, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus SURFACE = register("surface", NOISE, WORLDGEN_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus CARVERS = register("carvers", SURFACE, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus FEATURES = register("features", CARVERS, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus INITIALIZE_LIGHT = register("initialize_light", FEATURES, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus LIGHT = register("light", INITIALIZE_LIGHT, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus SPAWN = register("spawn", LIGHT, FINAL_HEIGHTMAPS, ChunkType.PROTOCHUNK);
    public static final ChunkStatus FULL = register("full", SPAWN, FINAL_HEIGHTMAPS, ChunkType.LEVELCHUNK);
    public static final Codec<ChunkStatus> CODEC = BuiltInRegistries.CHUNK_STATUS.byNameCodec();
    private final int index;
    private final ChunkStatus parent;
    private final ChunkType chunkType;
    private final EnumSet<Heightmap.Types> heightmapsAfter;

    private static ChunkStatus register(String name, @Nullable ChunkStatus parent, EnumSet<Heightmap.Types> heightmaps, ChunkType chunkType) {
        return Registry.register(BuiltInRegistries.CHUNK_STATUS, name, new ChunkStatus(parent, heightmaps, chunkType));
    }

    public static List<ChunkStatus> getStatusList() {
        List<ChunkStatus> list = Lists.newArrayList();

        ChunkStatus status;
        for (status = FULL; status.getParent() != status; status = status.getParent()) {
            list.add(status);
        }

        list.add(status);
        Collections.reverse(list);
        return list;
    }

    @VisibleForTesting
    protected ChunkStatus(@Nullable ChunkStatus parent, EnumSet<Heightmap.Types> heightmapsAfter, ChunkType chunkType) {
        this.parent = parent == null ? this : parent;
        this.chunkType = chunkType;
        this.heightmapsAfter = heightmapsAfter;
        this.index = parent == null ? 0 : parent.getIndex() + 1;

        EnumSet<Heightmap.Types> chunkSaveHeightmaps = EnumSet.copyOf(this.heightmapsAfter);
        if (this.chunkType != ChunkType.LEVELCHUNK) {
            chunkSaveHeightmaps.add(Heightmap.Types.WORLD_SURFACE_WG);
            chunkSaveHeightmaps.add(Heightmap.Types.OCEAN_FLOOR_WG);
        }
        this.chunkSaveHeightmaps = chunkSaveHeightmaps;
    }

    public int getIndex() {
        return this.index;
    }

    public ChunkStatus getParent() {
        return this.parent;
    }

    public ChunkType getChunkType() {
        return this.chunkType;
    }

    public static ChunkStatus byName(String key) {
        return BuiltInRegistries.CHUNK_STATUS.getValue(Identifier.tryParse(key));
    }

    public EnumSet<Heightmap.Types> heightmapsAfter() {
        return this.heightmapsAfter;
    }

    public boolean isOrAfter(ChunkStatus step) {
        return this.getIndex() >= step.getIndex();
    }

    public boolean isAfter(ChunkStatus step) {
        return this.getIndex() > step.getIndex();
    }

    public boolean isOrBefore(ChunkStatus step) {
        return this.getIndex() <= step.getIndex();
    }

    public boolean isBefore(ChunkStatus step) {
        return this.getIndex() < step.getIndex();
    }

    public static ChunkStatus max(ChunkStatus a, ChunkStatus b) {
        return a.isAfter(b) ? a : b;
    }

    @Override
    public String toString() {
        return this.getName();
    }

    public String getName() {
        return BuiltInRegistries.CHUNK_STATUS.getKey(this).toString();
    }

    /**
     * Neo: Internal use only.
     * Patch to fix [MC-308222](https://report.bugs.mojang.com/servicedesk/customer/portal/2/MC-308222)
     * intended for loading a non-fully generated chunk from disk to keep worldgen heightmap data for
     * structures and features to heightmap snap properly again.
     * @return A set of the chunk status's heightmaps alongside _WG heightmaps for worldgen-only chunk statuses
     */
    public EnumSet<Heightmap.Types> getChunkSaveHeightmaps() {
        return this.chunkSaveHeightmaps;
    }
    private final EnumSet<Heightmap.Types> chunkSaveHeightmaps;
}
