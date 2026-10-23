package dev.pricemonitor.scrape;

import dev.pricemonitor.site.Site;
import java.math.BigDecimal;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Разбор страницы товара по профилю сайта. Без сети — удобно тестировать на сохранённом HTML. */
public final class PageParser {

    public record ParsedPage(String name, BigDecimal price, boolean available) {
    }

    private PageParser() {
    }

    public static ParsedPage parse(Document page, Site site) {
        String name = site.nameSelector() == null ? page.title() : textOf(page, site.nameSelector());
        Element priceElement = page.selectFirst(site.priceSelector());
        if (priceElement == null) {
            throw new ScrapeException("Цена не найдена по селектору " + site.priceSelector()
                    + " — возможно, изменилась вёрстка страницы");
        }
        BigDecimal price = PriceParser.parse(priceElement.text())
                .orElseThrow(() -> new ScrapeException("Не удалось распознать цену: \"" + priceElement.text() + "\""));
        return new ParsedPage(name, price, isAvailable(page, site));
    }

    private static boolean isAvailable(Document page, Site site) {
        if (site.availabilitySelector() == null) {
            return true;
        }
        Element stock = page.selectFirst(site.availabilitySelector());
        // Блока наличия нет — на многих сайтах так выглядит «нет в наличии».
        if (stock == null) {
            return false;
        }
        return site.inStockPattern() == null || site.inStockPattern().matcher(stock.text()).find();
    }

    private static String textOf(Document page, String selector) {
        Element element = page.selectFirst(selector);
        return element != null ? element.text().trim() : null;
    }
}
