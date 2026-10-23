package dev.pricemonitor.scrape;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Достаёт число из текста цены: "£51.77", "1 299,00 руб.", "1,299.99 $", "45 BYN".
 * Разделитель дробной части определяется по последнему знаку и числу цифр после него.
 */
public final class PriceParser {

    private PriceParser() {
    }

    public static Optional<BigDecimal> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String raw = text.replaceAll("[^0-9.,]", "");
        if (raw.isEmpty() || raw.chars().noneMatch(Character::isDigit)) {
            return Optional.empty();
        }
        raw = trimSeparators(raw);
        int lastDot = raw.lastIndexOf('.');
        int lastComma = raw.lastIndexOf(',');
        int lastSep = Math.max(lastDot, lastComma);
        String normalized;
        if (lastSep < 0) {
            normalized = raw;
        } else if (lastDot >= 0 && lastComma >= 0) {
            // Оба знака: последний — дробный, второй — разделитель тысяч.
            normalized = raw.substring(0, lastSep).replaceAll("[.,]", "") + "." + raw.substring(lastSep + 1);
        } else {
            char sep = raw.charAt(lastSep);
            int digitsAfter = raw.length() - lastSep - 1;
            boolean single = raw.indexOf(sep) == lastSep;
            // "1,299" и "1.299.000" — тысячи; "45,90" и "51.77" — дробная часть.
            normalized = single && digitsAfter <= 2
                    ? raw.replace(sep, '.')
                    : raw.replaceAll("[.,]", "");
        }
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String trimSeparators(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && !Character.isDigit(raw.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isDigit(raw.charAt(end - 1))) {
            end--;
        }
        return raw.substring(start, end);
    }
}
