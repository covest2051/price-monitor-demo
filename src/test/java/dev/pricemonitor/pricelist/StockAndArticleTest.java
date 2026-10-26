package dev.pricemonitor.pricelist;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pricemonitor.pricelist.PriceListProperties.Brand;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockAndArticleTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "есть        | IN_STOCK     | есть",
            "+           | IN_STOCK     | есть",
            "12          | IN_STOCK     | 12 шт.",
            ">50         | IN_STOCK     | более 50",
            "0           | OUT_OF_STOCK | нет",
            "-           | OUT_OF_STOCK | нет",
            "Нет         | OUT_OF_STOCK | нет",
            "под заказ   | ON_ORDER     | под заказ",
            "ожидается   | ON_ORDER     | под заказ",
            "уточняйте   | UNKNOWN      | уточняйте"
    })
    void parsesStockWrittenDifferently(String raw, Stock.Status status, String text) {
        assertThat(Stock.parse(raw)).isEqualTo(new Stock(status, text));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0 986 452 041   | 0986452041",
            "13.0460-7184.2  | 13046071842",
            "W 712/75        | W71275",
            // русские С, В, Е, Р, К вместо латинских
            "СUK2939         | CUK2939",
            "ВКR6Е           | BKR6E",
            "5РК1135         | 5PK1135"
    })
    void normalizesArticles(String raw, String key) {
        assertThat(ArticleKeys.normalize(raw)).isEqualTo(key);
    }

    @Test
    void findsBrandByAliasAndInsideName() {
        ArticleKeys keys = new ArticleKeys(List.of(
                new Brand("MANN-FILTER", List.of("MANN", "MANN FILTER")),
                new Brand("ATE", List.of())));

        assertThat(keys.brand("Mann")).isEqualTo("MANN-FILTER");
        assertThat(keys.brand("Valeo")).isEqualTo("VALEO");
        assertThat(keys.brandInName("Фильтр масляный MANN W712/75")).isEqualTo("MANN-FILTER");
        // "ATE" не находится внутри слов
        assertThat(keys.brandInName("Термостат GATES")).isEmpty();
    }
}
