package dev.asteriamc.enhancedpets.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9A-F]{6})");
    private static final Pattern SECTION_HEX_PATTERN = Pattern.compile("§x(§[0-9A-Fa-f]){6}");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("(?i)&<#([0-9A-F]{6}):#([0-9A-F]{6})>");

    private ColorUtil() {
    }

    /**
     * Translates legacy color codes using '&', hex colors in the form of &#RRGGBB,
     * and gradients in the form of &<#RRGGBB:#RRGGBB>text
     * into Spigot-compatible section sign sequences.
     *
     * @param input the raw string from config
     * @return colored string or null if input is null
     */
    public static String colorize(String input) {
        if (input == null)
            return null;

        // First, process gradients
        String withGradients = applyGradients(input);

        // Then translate legacy color codes
        String withLegacy = ChatColor.translateAlternateColorCodes('&', withGradients);

        // Finally, process remaining hex codes (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(withLegacy);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Colorize a string for use as an item display name.
     * Prepends §r (reset) to disable Minecraft's default italic styling on custom
     * item names.
     *
     * @param input the raw string from config
     * @return colored string with italics disabled, suitable for item names
     */
    public static String itemName(String input) {
        if (input == null)
            return null;
        return "§r" + colorize(input);
    }

    /**
     * Convert section-sign colored text back to config-friendly form:
     * - Hex sequences like §x§R§R§G§G§B§B become &#RRGGBB
     * - Remaining section signs '§' become '&'
     */
    public static String decolorizeToAmpersandHex(String input) {
        if (input == null)
            return null;

        String converted = SECTION_HEX_PATTERN.matcher(input).replaceAll(match -> {
            String seq = match.group();
            StringBuilder hex = new StringBuilder();

            for (int i = 2; i < seq.length(); i += 2) {
                char c = seq.charAt(i + 1);
                hex.append(Character.toUpperCase(c));
            }
            return "&#" + hex;
        });

        return converted.replace('§', '&');
    }

    /**
     * Reverse-engineer gradients from colorized text back to gradient config
     * format.
     * Detects sequences of &#RRGGBB colors that form a gradient and converts them
     * back to &<#START:#END>text format.
     *
     * @param input the decolorized text (using ampersand format with &#RRGGBB)
     * @return text with gradients reconstructed in config format, or original if no
     *         gradients detected
     */
    public static String reverseEngineerGradients(String input) {
        if (input == null || input.isEmpty())
            return input;

        // Pattern to match &#RRGGBB followed by optional formatting codes and a
        // character
        Pattern hexPattern = Pattern.compile("&#([0-9A-F]{6})((?:&[lnokmr])*)(.)");
        Matcher matcher = hexPattern.matcher(input);

        List<ColoredChar> coloredChars = new ArrayList<>();

        // Extract all colored characters
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String formatting = matcher.group(2);
            String character = matcher.group(3);

            coloredChars.add(new ColoredChar(
                    matcher.start(),
                    matcher.end(),
                    hexColor,
                    formatting,
                    character));
        }

        // Need at least 3 colored chars to form a gradient
        if (coloredChars.size() < 3) {
            return input;
        }

        // Try to find gradient sequences
        StringBuilder result = new StringBuilder();
        int pos = 0;
        int i = 0;

        while (i < coloredChars.size()) {
            // Try to find a gradient starting at position i
            int gradientEnd = findGradientEnd(coloredChars, i);

            if (gradientEnd - i >= 3) {
                // Found a gradient!
                ColoredChar first = coloredChars.get(i);
                ColoredChar last = coloredChars.get(gradientEnd - 1);

                // Append text before this gradient
                if (pos < first.start) {
                    result.append(input, pos, first.start);
                }

                // Build gradient text
                StringBuilder gradientText = new StringBuilder();
                for (int j = i; j < gradientEnd; j++) {
                    ColoredChar cc = coloredChars.get(j);
                    gradientText.append(cc.formatting).append(cc.character);
                }

                // Output gradient format
                result.append("&<#").append(first.hexColor).append(":#").append(last.hexColor).append(">")
                        .append(gradientText);

                pos = last.end;
                i = gradientEnd;
            } else {
                // Not a gradient, just append this colored char as-is
                ColoredChar cc = coloredChars.get(i);
                if (pos < cc.start) {
                    result.append(input, pos, cc.start);
                }
                result.append("&#").append(cc.hexColor).append(cc.formatting).append(cc.character);
                pos = cc.end;
                i++;
            }
        }

        // Append any remaining text
        if (pos < input.length()) {
            result.append(input.substring(pos));
        }

        return result.toString();
    }

    /**
     * Find the end of a gradient sequence starting at index start
     * Returns the index after the last element in the gradient
     */
    private static int findGradientEnd(List<ColoredChar> chars, int start) {
        if (start >= chars.size() - 2)
            return start + 1;

        List<String> hexColors = new ArrayList<>();
        for (int i = start; i < chars.size(); i++) {
            hexColors.add(chars.get(i).hexColor);

            // Check if next color would break the gradient pattern
            if (i < chars.size() - 1) {
                // If there's a gap in positions, gradient ends
                if (chars.get(i + 1).start != chars.get(i).end) {
                    return i + 1;
                }
            }

            // Need at least 3 colors to check for gradient
            if (hexColors.size() >= 3) {
                // Check if this forms a linear gradient
                if (!isLinearGradient(hexColors)) {
                    // Not a gradient, return previous position
                    return i;
                }
            }
        }

        return chars.size();
    }

    /**
     * Check if a sequence of hex colors forms a linear gradient
     */
    private static boolean isLinearGradient(List<String> hexColors) {
        if (hexColors.size() < 3)
            return false;

        // Parse RGB values
        List<int[]> rgbs = new ArrayList<>();
        for (String hex : hexColors) {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            rgbs.add(new int[] { r, g, b });
        }

        // Check if RGB values change linearly (with some tolerance)
        int[] first = rgbs.get(0);
        int[] last = rgbs.get(rgbs.size() - 1);

        for (int i = 1; i < rgbs.size() - 1; i++) {
            double t = (double) i / (rgbs.size() - 1);

            // Expected values if it's a perfect gradient
            int expectedR = (int) (first[0] + (last[0] - first[0]) * t);
            int expectedG = (int) (first[1] + (last[1] - first[1]) * t);
            int expectedB = (int) (first[2] + (last[2] - first[2]) * t);

            int[] actual = rgbs.get(i);

            // Allow small tolerance for rounding
            int tolerance = 3;
            if (Math.abs(actual[0] - expectedR) > tolerance ||
                    Math.abs(actual[1] - expectedG) > tolerance ||
                    Math.abs(actual[2] - expectedB) > tolerance) {
                return false;
            }
        }

        return true;
    }

    private static class ColoredChar {
        int start;
        int end;
        String hexColor;
        String formatting;
        String character;

        ColoredChar(int start, int end, String hexColor, String formatting, String character) {
            this.start = start;
            this.end = end;
            this.hexColor = hexColor;
            this.formatting = formatting;
            this.character = character;
        }
    }

    /**
     * Applies gradient coloring to text using the format &<#RRGGBB:#RRGGBB>text
     * Gradient stops when encountering actual color codes (&0-9a-f, &#RRGGBB) or
     * another gradient tag
     */
    private static String applyGradients(String input) {
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this gradient
            result.append(input, lastEnd, matcher.start());

            String startHex = matcher.group(1);
            String endHex = matcher.group(2);

            // Find the text after the gradient tag
            int gradientStart = matcher.end();
            String remaining = input.substring(gradientStart);

            // Find where gradient should stop (at color code, new gradient, or end of
            // string)
            int stopIndex = findGradientStopIndex(remaining);
            String textToGradient = remaining.substring(0, stopIndex);

            // Apply gradient to the text
            String gradientedText = applyGradient(textToGradient, startHex, endHex);
            result.append(gradientedText);

            // Update position
            lastEnd = gradientStart + stopIndex;
        }

        // Append remaining text
        result.append(input.substring(lastEnd));
        return result.toString();
    }

    /**
     * Finds where the gradient should stop applying
     * Stops at: actual color codes (&0-9a-f, &#RRGGBB), new gradient (&<), or end
     * of string
     * Ignores formatting codes like &l, &n, &o, &m, &k, &r
     */
    private static int findGradientStopIndex(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Check if this is a color code (not formatting code)
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);

                // Stop at new gradient tag (&<)
                if (next == '<') {
                    return i;
                }

                // Color codes: 0-9, a-f, A-F, #
                // Formatting codes to ignore: l, n, o, m, k, r (and their uppercase)
                if ((next >= '0' && next <= '9') ||
                        (next >= 'a' && next <= 'f') ||
                        (next >= 'A' && next <= 'F') ||
                        next == '#') {
                    return i;
                }
            }
        }
        return text.length();
    }

    /**
     * Applies a gradient from startHex to endHex across the given text
     * Formatting codes (&l, &n, etc.) are preserved and don't count toward gradient
     * progression
     */
    private static String applyGradient(String text, String startHex, String endHex) {
        if (text.isEmpty())
            return text;

        // Parse RGB values
        int startR = Integer.parseInt(startHex.substring(0, 2), 16);
        int startG = Integer.parseInt(startHex.substring(2, 4), 16);
        int startB = Integer.parseInt(startHex.substring(4, 6), 16);

        int endR = Integer.parseInt(endHex.substring(0, 2), 16);
        int endG = Integer.parseInt(endHex.substring(2, 4), 16);
        int endB = Integer.parseInt(endHex.substring(4, 6), 16);

        // First pass: count actual visible characters (excluding formatting codes)
        int visibleChars = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                // Skip formatting codes: l, n, o, m, k, r
                if (next == 'l' || next == 'L' || next == 'n' || next == 'N' ||
                        next == 'o' || next == 'O' || next == 'm' || next == 'M' ||
                        next == 'k' || next == 'K' || next == 'r' || next == 'R') {
                    i++; // Skip the next character too
                    continue;
                }
            }
            visibleChars++;
        }

        // Second pass: apply gradient to visible characters
        StringBuilder result = new StringBuilder();
        int currentVisibleIndex = 0;
        StringBuilder activeFormatting = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Check if this is a formatting code
            if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                // Formatting codes: l, n, o, m, k, r
                if (next == 'l' || next == 'L' || next == 'n' || next == 'N' ||
                        next == 'o' || next == 'O' || next == 'm' || next == 'M' ||
                        next == 'k' || next == 'K' || next == 'r' || next == 'R') {
                    // Add to active formatting (will be applied after every color)
                    activeFormatting.append(c).append(next);
                    i++; // Skip next character
                    continue;
                }
            }

            // Calculate interpolation factor for this visible character
            double factor = visibleChars == 1 ? 0.0 : (double) currentVisibleIndex / (visibleChars - 1);

            // Interpolate RGB values
            int r = (int) (startR + (endR - startR) * factor);
            int g = (int) (startG + (endG - startG) * factor);
            int b = (int) (startB + (endB - startB) * factor);

            // Convert to hex and apply - color first, then active formatting, then
            // character
            String hex = String.format("%02X%02X%02X", r, g, b);
            result.append("&#").append(hex);

            // Apply all active formatting codes after EVERY color
            // This is needed because color codes reset formatting in Minecraft
            if (activeFormatting.length() > 0) {
                result.append(activeFormatting);
            }

            result.append(c);

            currentVisibleIndex++;
        }

        return result.toString();
    }
}
