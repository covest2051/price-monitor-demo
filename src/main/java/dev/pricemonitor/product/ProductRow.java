package dev.pricemonitor.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Строка таблицы на главной и в Excel: текущее состояние товара и изменение цены. */
public record ProductRow(
        ProductEntity product,
        String siteName,
        PriceCheckEntity lastCheck,
        BigDecimal price,
        BigDecimal previousPrice,
        Boolean available) {

    public BigDecimal change() {
        return price != null && previousPrice != null ? price.subtract(previousPrice) : null;
    }

    public BigDecimal changePercent() {
        BigDecimal change = change();
        if (change == null || previousPrice.signum() == 0) {
            return null;
        }
        return change.multiply(BigDecimal.valueOf(100)).divide(previousPrice, 1, RoundingMode.HALF_UP);
    }

    public Instant lastCheckedAt() {
        return lastCheck != null ? lastCheck.getCheckedAt() : null;
    }

    public String lastError() {
        return lastCheck != null && !lastCheck.isOk() ? lastCheck.getError() : null;
    }
}
