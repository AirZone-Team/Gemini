package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

public class ComponentUtils {
    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    @CheckReturnValue
    public static MutableComponent mergeStyles(MutableComponent component, Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style inner = component.getStyle();
            if (inner.isEmpty()) {
                return component.setStyle(style);
            } else {
                return inner.equals(style) ? component : component.setStyle(inner.applyTo(style));
            }
        }
    }

    @CheckReturnValue
    public static Component mergeStyles(Component component, Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style inner = component.getStyle();
            if (inner.isEmpty()) {
                return component.copy().setStyle(style);
            } else {
                return inner.equals(style) ? component : component.copy().setStyle(inner.applyTo(style));
            }
        }
    }

    public static Optional<MutableComponent> resolve(ResolutionContext context, Optional<Component> component, int recursionDepth) throws CommandSyntaxException {
        return component.isPresent() ? Optional.of(resolve(context, component.get(), recursionDepth)) : Optional.empty();
    }

    public static MutableComponent resolve(ResolutionContext context, Component component) throws CommandSyntaxException {
        return resolve(context, component, 0);
    }

    public static MutableComponent resolve(ResolutionContext context, Component component, int recursionDepth) throws CommandSyntaxException {
        if (recursionDepth > context.depthLimit()) {
            return switch (context.depthLimitBehavior()) {
                case DISCARD_REMAINING -> CommonComponents.ELLIPSIS.copy();
                case STOP_PROCESSING_AND_COPY_REMAINING -> component.copy();
            };
        } else {
            MutableComponent result = component.getContents().resolve(context, recursionDepth + 1);

            for (Component sibling : component.getSiblings()) {
                result.append(resolve(context, sibling, recursionDepth + 1));
            }

            return result.withStyle(resolveStyle(context, component.getStyle(), recursionDepth));
        }
    }

    private static Style resolveStyle(ResolutionContext context, Style style, int recursionDepth) throws CommandSyntaxException {
        if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component text)) {
            HoverEvent resolved = new HoverEvent.ShowText(resolve(context, text, recursionDepth + 1));
            return style.withHoverEvent(resolved);
        } else {
            return style;
        }
    }

    public static Component formatList(Collection<String> values) {
        return formatAndSortList(values, v -> Component.literal(v).withStyle(ChatFormatting.GREEN));
    }

    public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> values, Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return CommonComponents.EMPTY;
        }

        if (values.size() == 1) {
            return formatter.apply(values.iterator().next());
        }

        List<T> sorted = Lists.newArrayList(values);
        sorted.sort(Comparable::compareTo);
        return formatList(sorted, formatter);
    }

    public static <T> Component formatList(Collection<? extends T> values, Function<T, Component> formatter) {
        return formatList(values, DEFAULT_SEPARATOR, formatter);
    }

    public static <T> MutableComponent formatList(Collection<? extends T> values, Optional<? extends Component> separator, Function<T, Component> formatter) {
        return formatList(values, DataFixUtils.orElse(separator, DEFAULT_SEPARATOR), formatter);
    }

    public static Component formatList(Collection<? extends Component> values, Component separator) {
        return formatList(values, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(Collection<? extends T> values, Component separator, Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return Component.empty();
        }

        if (values.size() == 1) {
            return formatter.apply((T)values.iterator().next()).copy();
        }

        MutableComponent result = Component.empty();
        boolean first = true;

        for (T value : values) {
            if (!first) {
                result.append(separator);
            }

            result.append(formatter.apply(value));
            first = false;
        }

        return result;
    }

    public static MutableComponent wrapInSquareBrackets(Component inner) {
        return Component.translatable("chat.square_brackets", inner);
    }

    public static Component fromMessage(Message message) {
        return message instanceof Component component ? component : Component.literal(message.getString());
    }

    public static boolean isTranslationResolvable(@Nullable Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            String fallback = translatable.getFallback();
            return fallback != null || Language.getInstance().has(key);
        } else {
            return true;
        }
    }

    public static MutableComponent copyOnClickText(String text) {
        return wrapInSquareBrackets(
            Component.literal(text)
                .withStyle(
                    s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.CopyToClipboard(text))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))
                        .withInsertion(text)
                )
        );
    }
}
