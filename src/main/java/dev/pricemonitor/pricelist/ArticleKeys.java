package dev.pricemonitor.pricelist;

import dev.pricemonitor.pricelist.PriceListProperties.Brand;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Приводит артикулы и бренды к виду, по которому один товар узнаётся у разных поставщиков. */
public final class ArticleKeys {

    // Кириллица, похожая на латиницу: в выгрузках из 1С "СUK2939" нередко набран русской "С".
    private static final String CYRILLIC = "АВЕКМНОРСТХУ";
    private static final String LATIN = "ABEKMHOPCTXY";

    private final Map<String, String> brandByAlias = new HashMap<>();
    private final Map<Pattern, String> brandInName = new LinkedHashMap<>();

    public ArticleKeys(List<Brand> brands) {
        Map<String, String> aliases = new HashMap<>();
        for (Brand brand : brands) {
            aliases.put(brand.name(), brand.name());
            brand.aliases().forEach(alias -> aliases.put(alias, brand.name()));
        }
        aliases.forEach((alias, brand) -> brandByAlias.put(normalize(alias), brand));
        // Длинные варианты раньше коротких: "MANN FILTER" должен найтись раньше, чем "MANN".
        aliases.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .forEach(alias -> brandInName.put(Pattern.compile(
                        "(^|[^\\p{L}\\p{N}])" + Pattern.quote(alias) + "($|[^\\p{L}\\p{N}])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), aliases.get(alias)));
    }

    /** "0 986-452.041", "сuk 2939" → "0986452041", "CUK2939". */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        for (char ch : text.toUpperCase(Locale.ROOT).toCharArray()) {
            int cyrillic = CYRILLIC.indexOf(ch);
            char c = cyrillic >= 0 ? LATIN.charAt(cyrillic) : ch;
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
                key.append(c);
            }
        }
        return key.toString();
    }

    /** Бренд из справочника; незнакомый бренд остаётся как есть, заглавными буквами. */
    public String brand(String raw) {
        String known = brandByAlias.get(normalize(raw));
        return known != null ? known : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** Бренд из названия, если у поставщика нет отдельной колонки: "Фильтр масляный MANN W712/75". */
    public String brandInName(String name) {
        if (name == null) {
            return "";
        }
        return brandInName.entrySet().stream()
                .filter(entry -> entry.getKey().matcher(name).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }
}
