package dev.pricemonitor.pricelist;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Наличие у поставщика: статус для сравнения и текст для отчёта. */
public record Stock(Status status, String text) {

    private static final Pattern QUANTITY = Pattern.compile("^(>|более|больше|свыше)?\\s*(\\d+)\\s*(\\+|шт\\.?)?$");

    public enum Status { IN_STOCK, ON_ORDER, OUT_OF_STOCK, UNKNOWN }

    public boolean inStock() {
        return status == Status.IN_STOCK;
    }

    /** "есть", "+", "12", "&gt;50", "нет", "0", "-", "под заказ", "ожидается" — у каждого поставщика по-своему. */
    public static Stock parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (text.isEmpty()) {
            return new Stock(Status.UNKNOWN, "");
        }
        if (text.matches("0+|-|—|нет|нет в наличии|отсутствует")) {
            return new Stock(Status.OUT_OF_STOCK, "нет");
        }
        if (text.contains("заказ") || text.startsWith("ожида")) {
            return new Stock(Status.ON_ORDER, "под заказ");
        }
        Matcher quantity = QUANTITY.matcher(text);
        if (quantity.matches()) {
            boolean atLeast = quantity.group(1) != null || "+".equals(quantity.group(3));
            return new Stock(Status.IN_STOCK, atLeast ? "более " + quantity.group(2) : quantity.group(2) + " шт.");
        }
        if (text.matches("\\+|есть|да|в наличии|много|достаточно")) {
            return new Stock(Status.IN_STOCK, "есть");
        }
        return new Stock(Status.UNKNOWN, raw.trim());
    }
}
