package dev.pricemonitor.pricelist;

import java.math.BigDecimal;

/**
 * Позиция прайса после приведения к общему виду.
 *
 * @param article    артикул как в прайсе, для показа
 * @param articleKey артикул для сравнения: только латинские буквы и цифры
 * @param price      цена с НДС
 */
public record Offer(
        String supplierCode,
        String fileName,
        int line,
        String category,
        String brand,
        String article,
        String articleKey,
        String name,
        BigDecimal price,
        Stock stock) {

    public String key() {
        return brand + " " + articleKey;
    }
}
