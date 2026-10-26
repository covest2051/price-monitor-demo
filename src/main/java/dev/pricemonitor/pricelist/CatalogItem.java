package dev.pricemonitor.pricelist;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Товар единого каталога: один бренд и артикул, предложения всех поставщиков. */
public record CatalogItem(String key, String brand, String article, String name, Map<String, Offer> offers) {

    public Offer offer(String supplierCode) {
        return offers.get(supplierCode);
    }

    /** Самое дешёвое предложение среди тех, у кого товар есть в наличии. */
    public Offer best() {
        return offers.values().stream()
                .filter(offer -> offer.stock().inStock())
                .min(Comparator.comparing(Offer::price))
                .orElse(null);
    }

    public boolean isBest(Offer offer) {
        Offer best = best();
        return best != null && offers.size() > 1 && best == offer;
    }

    /** Насколько самое дорогое предложение дороже самого дешёвого; null, если поставщик один. */
    public BigDecimal spread() {
        if (offers.size() < 2) {
            return null;
        }
        BigDecimal min = offers.values().stream().map(Offer::price).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = offers.values().stream().map(Offer::price).max(BigDecimal::compareTo).orElseThrow();
        return max.subtract(min);
    }

    /**
     * Собирает каталог из позиций всех поставщиков (в порядке профилей). Товар узнаётся по бренду и артикулу.
     * Позиция без бренда присоединяется к товару с тем же артикулом, если такой артикул только у одного бренда.
     */
    static List<CatalogItem> build(List<Offer> offers) {
        Map<String, List<Offer>> byKey = new LinkedHashMap<>();
        Map<String, List<String>> brandsByArticle = new LinkedHashMap<>();
        for (Offer offer : offers) {
            if (!offer.brand().isEmpty()) {
                List<String> brands = brandsByArticle.computeIfAbsent(offer.articleKey(), k -> new ArrayList<>());
                if (!brands.contains(offer.brand())) {
                    brands.add(offer.brand());
                }
            }
        }
        for (Offer offer : offers) {
            String key = offer.key();
            List<String> brands = brandsByArticle.getOrDefault(offer.articleKey(), List.of());
            if (offer.brand().isEmpty() && brands.size() == 1) {
                key = brands.getFirst() + " " + offer.articleKey();
            }
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(offer);
        }
        List<CatalogItem> items = new ArrayList<>();
        byKey.forEach((key, group) -> {
            Map<String, Offer> bySupplier = new LinkedHashMap<>();
            group.forEach(offer -> bySupplier.putIfAbsent(offer.supplierCode(), offer));
            String brand = group.stream().map(Offer::brand).filter(b -> !b.isEmpty()).findFirst().orElse("");
            // Название — самое короткое: у части поставщиков в названии продублированы бренд и артикул.
            String name = group.stream().map(Offer::name).filter(n -> !n.isEmpty())
                    .min(Comparator.comparingInt(String::length)).orElse("");
            items.add(new CatalogItem(key, brand, group.getFirst().article(), name, bySupplier));
        });
        items.sort(Comparator.comparing(CatalogItem::brand).thenComparing(CatalogItem::key));
        return items;
    }
}
