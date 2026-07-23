package net.minecraft.tags;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record TagFile(List<TagEntry> entries, boolean replace, List<TagEntry> remove) {
    public static final Codec<TagFile> CODEC = RecordCodecBuilder.create(
        i -> i.group(
                TagEntry.CODEC.listOf().fieldOf("values").forGetter(TagFile::entries),
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(TagFile::replace),
                TagEntry.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(TagFile::remove)
            )
            .apply(i, TagFile::new)
    );

    // Neo: Preserve vanilla constructor
    public TagFile(List<TagEntry> entries, boolean replace) {
        this(entries, replace, List.of());
    }
}
