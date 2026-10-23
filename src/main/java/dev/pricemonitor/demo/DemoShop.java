package dev.pricemonitor.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Встроенный демо-магазин: цены меняются раз в несколько дней, товары иногда заканчиваются.
 * Нужен, чтобы показать историю и изменения цен, не нагружая чужие сайты.
 */
public final class DemoShop {

    public record Item(int id, String name, BigDecimal basePrice) {
    }

    public record Offer(Item item, BigDecimal price, boolean available) {
    }

    public static final List<Item> ITEMS = List.of(
            new Item(1, "Кофемашина автоматическая, 15 бар", new BigDecimal("1299")),
            new Item(2, "Робот-пылесос с влажной уборкой", new BigDecimal("689")),
            new Item(3, "Беспроводные наушники с шумоподавлением", new BigDecimal("1049")),
            new Item(4, "Смартфон 8/256 ГБ", new BigDecimal("1199")),
            new Item(5, "Электрочайник 1,7 л", new BigDecimal("119")));

    private DemoShop() {
    }

    public static Optional<Item> item(int id) {
        return ITEMS.stream().filter(i -> i.id() == id).findFirst();
    }

    /** Детерминированно по дате: одна и та же дата всегда даёт ту же цену. */
    public static Offer offer(Item item, LocalDate date) {
        int periodDays = 1 + item.id() % 3;
        long step = Math.floorDiv(date.toEpochDay() + item.id(), periodDays);
        double factor = 1 + 0.06 * Math.sin(step * 1.3 + item.id());
        BigDecimal price = item.basePrice().multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .subtract(new BigDecimal("0.10"));
        boolean available = (step + item.id()) % 7 != 0;
        return new Offer(item, price, available);
    }
}
