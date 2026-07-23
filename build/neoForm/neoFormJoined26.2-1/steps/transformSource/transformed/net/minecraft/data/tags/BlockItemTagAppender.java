package net.minecraft.data.tags;

import java.util.Arrays;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.WeatheringCopperCollection;

public abstract class BlockItemTagAppender<Element> implements TagAppender<Element> {
    private final TagAppender<Element> original;

    public BlockItemTagAppender(TagAppender<Element> original) {
        this.original = original;
    }

    protected abstract ResourceKey<Element> convertElement(BlockItemId element);

    public BlockItemTagAppender<Element> add(ResourceKey<Element> element) {
        this.original.add(element);
        return this;
    }

    public BlockItemTagAppender<Element> add(BlockItemId... ids) {
        this.original.addAll(Arrays.stream(ids).map(this::convertElement));
        return this;
    }

    public BlockItemTagAppender<Element> addAll(ColorCollection<ResourceKey<Element>> collection) {
        collection.forEach(this::add);
        return this;
    }

    public BlockItemTagAppender<Element> addAll(WeatheringCopperCollection<ResourceKey<Element>> collection) {
        collection.forEach(this::add);
        return this;
    }

    @SafeVarargs
    public final BlockItemTagAppender<Element> add(ResourceKey<Element>... elements) {
        this.original.add(elements);
        return this;
    }

    public BlockItemTagAppender<Element> addOptional(ResourceKey<Element> element) {
        this.original.addOptional(element);
        return this;
    }

    public BlockItemTagAppender<Element> addTag(TagKey<Element> tag) {
        this.original.addTag(tag);
        return this;
    }

    public BlockItemTagAppender<Element> addOptionalTag(TagKey<Element> tag) {
        this.original.addOptionalTag(tag);
        return this;
    }

    @Override
    public BlockItemTagAppender<Element> add(net.minecraft.tags.TagEntry entry) {
        original.add(entry);
        return this;
    }

    @Override
    public BlockItemTagAppender<Element> replace(boolean value) {
        original.replace(value);
        return this;
    }

    @Override
    public BlockItemTagAppender<Element> remove(ResourceKey<Element> block) {
        original.remove(block);
        return this;
    }

    @Override
    public BlockItemTagAppender<Element> remove(TagKey<Element> tag) {
        original.remove(tag);
        return this;
    }
}
