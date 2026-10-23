package dev.pricemonitor.scrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pricemonitor.scrape.PageParser.ParsedPage;
import dev.pricemonitor.site.Site;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class PageParserTest {

    private static final Site BOOKS = new Site("books", "books.toscrape.com", "Books to Scrape",
            "div.product_main h1", "div.product_main p.price_color", "div.product_main p.availability",
            Pattern.compile("In stock", Pattern.CASE_INSENSITIVE));

    /** Фрагмент реальной страницы books.toscrape.com. */
    private static final String BOOK_PAGE = """
            <div class="col-sm-6 product_main">
              <h1>A Light in the Attic</h1>
              <p class="price_color">£51.77</p>
              <p class="instock availability"><i class="icon-ok"></i> In stock (22 available)</p>
            </div>""";

    @Test
    void readsNamePriceAndAvailability() {
        ParsedPage page = PageParser.parse(Jsoup.parse(BOOK_PAGE), BOOKS);

        assertThat(page.name()).isEqualTo("A Light in the Attic");
        assertThat(page.price()).isEqualByComparingTo(new BigDecimal("51.77"));
        assertThat(page.available()).isTrue();
    }

    @Test
    void outOfStockWhenTextDoesNotMatch() {
        String html = BOOK_PAGE.replace("In stock (22 available)", "Out of stock");

        assertThat(PageParser.parse(Jsoup.parse(html), BOOKS).available()).isFalse();
    }

    @Test
    void layoutChangeIsReportedAsError() {
        String html = BOOK_PAGE.replace("price_color", "price-new");

        assertThatThrownBy(() -> PageParser.parse(Jsoup.parse(html), BOOKS))
                .isInstanceOf(ScrapeException.class)
                .hasMessageContaining("Цена не найдена");
    }
}
