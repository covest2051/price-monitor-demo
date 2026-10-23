package dev.pricemonitor.product;

/**
 * OK — страницу разобрали (товара может и не быть в наличии); ERROR — сбой сбора.
 * Их нельзя смешивать: «сайт лёг» и «товар закончился» для владельца — разные события.
 */
public enum CheckStatus {
    OK,
    ERROR
}
