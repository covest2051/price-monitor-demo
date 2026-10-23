package dev.pricemonitor.site;

import java.util.regex.Pattern;

public record Site(
        String code,
        String host,
        String name,
        String nameSelector,
        String priceSelector,
        String availabilitySelector,
        Pattern inStockPattern) {
}
