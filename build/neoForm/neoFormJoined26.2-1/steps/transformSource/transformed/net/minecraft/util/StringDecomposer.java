package net.minecraft.util;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class StringDecomposer {
    private static final char REPLACEMENT_CHAR = '\ufffd';
    private static final Optional<Object> STOP_ITERATION = Optional.of(Unit.INSTANCE);

    private static boolean feedChar(Style style, FormattedCharSink output, int pos, char ch) {
        return Character.isSurrogate(ch) ? output.accept(pos, style, 65533) : output.accept(pos, style, ch);
    }

    public static boolean iterate(String string, Style style, FormattedCharSink output) {
        int size = string.length();

        for (int i = 0; i < size; i++) {
            char ch = string.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= size) {
                    if (!output.accept(i, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char low = string.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    if (!output.accept(i, style, Character.toCodePoint(ch, low))) {
                        return false;
                    }

                    i++;
                } else if (!output.accept(i, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, output, i, ch)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateBackwards(String string, Style style, FormattedCharSink output) {
        int size = string.length();

        for (int i = size - 1; i >= 0; i--) {
            char ch = string.charAt(i);
            if (Character.isLowSurrogate(ch)) {
                if (i - 1 < 0) {
                    if (!output.accept(0, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char high = string.charAt(i - 1);
                if (Character.isHighSurrogate(high)) {
                    if (!output.accept(--i, style, Character.toCodePoint(high, ch))) {
                        return false;
                    }
                } else if (!output.accept(i, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, output, i, ch)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(String string, Style style, FormattedCharSink output) {
        return iterateFormatted(string, 0, style, output);
    }

    public static boolean iterateFormatted(String string, int offset, Style style, FormattedCharSink output) {
        return iterateFormatted(string, offset, style, style, output);
    }

    public static boolean iterateFormatted(String string, int offset, Style currentStyle, Style resetStyle, FormattedCharSink output) {
        int size = string.length();
        Style style = currentStyle;

        for (int i = offset; i < size; i++) {
            char ch = string.charAt(i);
            if (ch == 167) {
                if (i + 1 >= size) {
                    break;
                }

                char code = string.charAt(i + 1);
                ChatFormatting formatting = ChatFormatting.getByCode(code);
                if (formatting != null) {
                    style = formatting == ChatFormatting.RESET ? resetStyle : style.applyLegacyFormat(formatting);
                }

                i++;
            } else if (Character.isHighSurrogate(ch)) {
                if (i + 1 >= size) {
                    if (!output.accept(i, style, 65533)) {
                        return false;
                    }
                    break;
                }

                char low = string.charAt(i + 1);
                if (Character.isLowSurrogate(low)) {
                    if (!output.accept(i, style, Character.toCodePoint(ch, low))) {
                        return false;
                    }

                    i++;
                } else if (!output.accept(i, style, 65533)) {
                    return false;
                }
            } else if (!feedChar(style, output, i, ch)) {
                return false;
            }
        }

        return true;
    }

    public static boolean iterateFormatted(FormattedText component, Style rootStyle, FormattedCharSink output) {
        return component.visit((style, contents) -> iterateFormatted(contents, 0, style, output) ? Optional.empty() : STOP_ITERATION, rootStyle).isEmpty();
    }

    public static String filterBrokenSurrogates(String input) {
        StringBuilder builder = new StringBuilder();
        iterate(input, Style.EMPTY, (position, style, codepoint) -> {
            builder.appendCodePoint(codepoint);
            return true;
        });
        return builder.toString();
    }

    public static String getPlainText(FormattedText input) {
        StringBuilder builder = new StringBuilder();
        iterateFormatted(input, Style.EMPTY, (position, style, codepoint) -> {
            builder.appendCodePoint(codepoint);
            return true;
        });
        return builder.toString();
    }
}
