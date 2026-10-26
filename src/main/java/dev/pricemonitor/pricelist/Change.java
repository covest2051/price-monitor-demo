package dev.pricemonitor.pricelist;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Что изменилось у поставщика по сравнению с его прошлым прайсом. */
public record Change(
        Type type,
        String supplierCode,
        String supplierName,
        String brand,
        String article,
        String name,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        Stock oldStock,
        Stock newStock) {

    public enum Type {
        PRICE_UP("Подорожало"),
        PRICE_DOWN("Подешевело"),
        OUT_OF_STOCK("Закончилось"),
        BACK_IN_STOCK("Снова в наличии"),
        NEW("Новая позиция"),
        REMOVED("Пропало из прайса");

        private final String title;

        Type(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    public BigDecimal diff() {
        return oldPrice != null && newPrice != null ? newPrice.subtract(oldPrice) : null;
    }

    public BigDecimal percent() {
        BigDecimal diff = diff();
        return diff != null && oldPrice.signum() != 0
                ? diff.multiply(BigDecimal.valueOf(100)).divide(oldPrice, 1, RoundingMode.HALF_UP)
                : null;
    }

    /**
     * Сравнивает прошлый и текущий каталог по каждому поставщику из {@code suppliers}: у них есть оба прайса.
     * Порядок: подорожало, подешевело (сильнее — выше), закончилось, появилось, новые, пропавшие.
     */
    static List<Change> compare(List<CatalogItem> previous, List<CatalogItem> current, Map<String, String> suppliers) {
        Map<String, CatalogItem> before = previous.stream().collect(Collectors.toMap(CatalogItem::key, Function.identity()));
        Map<String, CatalogItem> now = current.stream().collect(Collectors.toMap(CatalogItem::key, Function.identity()));
        Set<String> keys = new LinkedHashSet<>(now.keySet());
        keys.addAll(before.keySet());
        List<Change> changes = new ArrayList<>();
        suppliers.forEach((code, supplierName) -> {
            for (String key : keys) {
                Offer was = before.containsKey(key) ? before.get(key).offer(code) : null;
                Offer is = now.containsKey(key) ? now.get(key).offer(code) : null;
                CatalogItem item = now.getOrDefault(key, before.get(key));
                if (was == null && is == null) {
                    continue;
                }
                if (was == null) {
                    changes.add(change(Type.NEW, code, supplierName, item, null, is));
                } else if (is == null) {
                    changes.add(change(Type.REMOVED, code, supplierName, item, was, null));
                } else {
                    int price = is.price().compareTo(was.price());
                    if (price != 0) {
                        changes.add(change(price > 0 ? Type.PRICE_UP : Type.PRICE_DOWN, code, supplierName, item, was, is));
                    }
                    if (was.stock().inStock() && isGone(is.stock())) {
                        changes.add(change(Type.OUT_OF_STOCK, code, supplierName, item, was, is));
                    } else if (isGone(was.stock()) && is.stock().inStock()) {
                        changes.add(change(Type.BACK_IN_STOCK, code, supplierName, item, was, is));
                    }
                }
            }
        });
        changes.sort(Comparator.comparing(Change::type)
                .thenComparing(change -> change.percent() != null ? change.percent().abs().negate() : BigDecimal.ZERO)
                .thenComparing(Change::brand)
                .thenComparing(Change::article));
        return changes;
    }

    private static boolean isGone(Stock stock) {
        return stock.status() == Stock.Status.OUT_OF_STOCK || stock.status() == Stock.Status.ON_ORDER;
    }

    private static Change change(Type type, String code, String supplierName, CatalogItem item, Offer was, Offer is) {
        return new Change(type, code, supplierName, item.brand(), item.article(), item.name(),
                was != null ? was.price() : null, is != null ? is.price() : null,
                was != null ? was.stock() : null, is != null ? is.stock() : null);
    }
}
