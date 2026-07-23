package net.minecraft.tags;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

public class TagEntry {
    private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(
        i -> i.group(
                ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag),
                Codec.BOOL.optionalFieldOf("required", true).forGetter(e -> e.required)
            )
            .apply(i, TagEntry::new)
    );
    public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC)
        .xmap(e -> e.map(l -> new TagEntry(l, true), r -> (TagEntry)r), entry -> entry.required ? Either.left(entry.elementOrTag()) : Either.right(entry));
    private final Identifier id;
    private final boolean tag;
    private final boolean required;

    private TagEntry(Identifier id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }

    private TagEntry(ExtraCodecs.TagOrElementLocation elementOrTag, boolean required) {
        this.id = elementOrTag.id();
        this.tag = elementOrTag.tag();
        this.required = required;
    }

    private ExtraCodecs.TagOrElementLocation elementOrTag() {
        return new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
    }

    /**
     * {@return a copy of this entry with the required flag set to the given parameter}
     *
     * @param required whether the new entry is required
     */
    TagEntry withRequired(boolean required) {
        return new TagEntry(this.id, this.tag, required);
    }

    public static TagEntry element(Identifier id) {
        return new TagEntry(id, false, true);
    }

    public static TagEntry optionalElement(Identifier id) {
        return new TagEntry(id, false, false);
    }

    public static TagEntry tag(Identifier id) {
        return new TagEntry(id, true, true);
    }

    public static TagEntry optionalTag(Identifier id) {
        return new TagEntry(id, true, false);
    }

    public <T> boolean build(TagEntry.Lookup<T> lookup, Consumer<T> output) {
        if (this.tag) {
            Collection<T> result = lookup.tag(this.id);
            if (result == null) {
                return !this.required;
            }

            result.forEach(output);
        } else {
            T result = lookup.element(this.id, this.required);
            if (result == null) {
                return !this.required;
            }

            output.accept(result);
        }

        return true;
    }

    public void visitRequiredDependencies(Consumer<Identifier> output) {
        if (this.tag && this.required) {
            output.accept(this.id);
        }
    }

    public void visitOptionalDependencies(Consumer<Identifier> output) {
        if (this.tag && !this.required) {
            output.accept(this.id);
        }
    }

    public boolean verifyIfPresent(Predicate<Identifier> elementCheck, Predicate<Identifier> tagCheck) {
        return !this.required || (this.tag ? tagCheck : elementCheck).test(this.id);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.tag) {
            result.append('#');
        }

        result.append(this.id);
        if (!this.required) {
            result.append('?');
        }

        return result.toString();
    }

    // Neo: Used in datagen
    public Identifier getId() {
        return id;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isTag() {
        return tag;
    }

    public interface Lookup<T> {
        @Nullable T element(Identifier key, boolean required);

        @Nullable Collection<T> tag(Identifier key);
    }
}
