package dev.pricemonitor.pricelist;

/** Строка прайса, которую не удалось взять в каталог: показывается пользователю, а не теряется молча. */
public record Problem(String supplierName, String fileName, Integer line, String reason, String content) {
}
