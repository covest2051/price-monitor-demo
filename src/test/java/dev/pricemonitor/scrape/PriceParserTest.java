package dev.pricemonitor.scrape;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriceParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "£51.77              | 51.77",
            "1 299,00 руб.       | 1299.00",
            "1 254,90 руб.      | 1254.90",
            "1,299.99 $          | 1299.99",
            "1.299,99 €          | 1299.99",
            "45 BYN              | 45",
            "1,299               | 1299",
            "1.299.000           | 1299000",
            "от 89,9 р.          | 89.9"
    })
    void parsesDifferentFormats(String text, String expected) {
        assertThat(PriceParser.parse(text)).contains(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Цена по запросу", "—"})
    void returnsEmptyWhenThereIsNoNumber(String text) {
        assertThat(PriceParser.parse(text)).isEmpty();
    }
}
