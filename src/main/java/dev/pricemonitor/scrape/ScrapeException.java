package dev.pricemonitor.scrape;

/** Страницу получили, но разобрать не смогли: сменилась вёрстка, нет цены и т. п. */
public class ScrapeException extends RuntimeException {

    public ScrapeException(String message) {
        super(message);
    }
}
