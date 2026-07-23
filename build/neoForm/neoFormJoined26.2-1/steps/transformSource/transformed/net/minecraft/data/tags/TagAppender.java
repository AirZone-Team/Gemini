package net.minecraft.data.tags;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

import net.neoforged.neoforge.common.extensions.ITagAppenderExtension;

public interface TagAppender<T> extends ITagAppenderExtension<T> {
    TagAppender<T> add(ResourceKey<T> element);

    default TagAppender<T> add(ResourceKey<T>... elements) {
        return this.addAll(Arrays.stream(elements));
    }

    default TagAppender<T> addAll(Collection<ResourceKey<T>> elements) {
        elements.forEach(this::add);
        return this;
    }

    default TagAppender<T> addAll(Stream<ResourceKey<T>> elements) {
        elements.forEach(this::add);
        return this;
    }

    TagAppender<T> addOptional(ResourceKey<T> element);

    TagAppender<T> addTag(TagKey<T> tag);

    TagAppender<T> addOptionalTag(TagKey<T> tag);

    static <T> TagAppender<T> forBuilder(TagBuilder builder) {
        return new TagAppender<T>() {
            @Override
            public TagAppender<T> add(ResourceKey<T> element) {
                builder.addElement(element.identifier());
                return this;
            }

            @Override
            public TagAppender<T> addOptional(ResourceKey<T> element) {
                builder.addOptionalElement(element.identifier());
                return this;
            }

            @Override
            public TagAppender<T> addTag(TagKey<T> tag) {
                builder.addTag(tag.location());
                return this;
            }

            @Override
            public TagAppender<T> addOptionalTag(TagKey<T> tag) {
                builder.addOptionalTag(tag.location());
                return this;
            }

            @Override
            public TagAppender<T> add(net.minecraft.tags.TagEntry entry) {
                builder.add(entry);
                return this;
            }

            @Override
            public TagAppender<T> replace(boolean value) {
                builder.setReplace(value);
                return this;
            }

            @Override
            public TagAppender<T> remove(TagKey<T> tag) {
                builder.remove(net.minecraft.tags.TagEntry.tag(tag.location()));
                return this;
            }

            @Override
            public TagAppender<T> remove(ResourceKey<T> element) {
                builder.removeElement(element.identifier());
                return this;
            }
        };
    }
}
