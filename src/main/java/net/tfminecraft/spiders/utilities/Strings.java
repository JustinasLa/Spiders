package net.tfminecraft.spiders.utilities;

/**
 * Trimming for command input. Unlike {@link String#strip()}, every Unicode space separator counts,
 * including the non-breaking space some clients paste.
 */
public final class Strings {
    private Strings() {
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    public static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) start++;
        while (end > start && isSpace(text.charAt(end - 1))) end--;
        return text.substring(start, end);
    }

    public static boolean isBlank(String text) {
        return trim(text).isEmpty();
    }
}
