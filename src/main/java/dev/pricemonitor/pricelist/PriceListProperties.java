package dev.pricemonitor.pricelist;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Профили поставщиков и справочник брендов из {@code pricelist.yml}. */
@ConfigurationProperties("pricelist")
public record PriceListProperties(BigDecimal vatPercent, List<Brand> brands, List<Supplier> suppliers) {

    public PriceListProperties {
        vatPercent = vatPercent != null ? vatPercent : new BigDecimal("20");
        brands = brands != null ? List.copyOf(brands) : List.of();
        suppliers = suppliers != null ? List.copyOf(suppliers) : List.of();
    }

    /** Бренд и то, как его пишут разные поставщики: "FEBI BILSTEIN", "Febi", "FEBI". */
    public record Brand(String name, List<String> aliases) {

        public Brand {
            aliases = aliases != null ? List.copyOf(aliases) : List.of();
        }
    }

    /**
     * Профиль поставщика: как узнать его файл и где в файле артикул, цена и наличие.
     * Новый поставщик подключается профилем, без изменения кода.
     */
    public record Supplier(
            String code,
            String name,
            String filePattern,
            Format format,
            Charset charset,
            String delimiter,
            Boolean pricesWithVat,
            Columns columns,
            String linePattern,
            String skipPattern) {

        public Supplier {
            charset = charset != null ? charset : StandardCharsets.UTF_8;
            delimiter = delimiter != null ? delimiter : ";";
            pricesWithVat = pricesWithVat != null ? pricesWithVat : Boolean.TRUE;
            columns = columns != null ? columns : new Columns(null, null, null, null, null);
        }

        public boolean matches(String fileName) {
            return Pattern.compile(filePattern).matcher(fileName).matches();
        }
    }

    /** Возможные заголовки колонок в таблице поставщика; сравниваются без учёта регистра и точки в конце. */
    public record Columns(List<String> article, List<String> brand, List<String> name, List<String> price,
                          List<String> stock) {

        public Columns {
            article = article != null ? List.copyOf(article) : List.of();
            brand = brand != null ? List.copyOf(brand) : List.of();
            name = name != null ? List.copyOf(name) : List.of();
            price = price != null ? List.copyOf(price) : List.of();
            stock = stock != null ? List.copyOf(stock) : List.of();
        }
    }

    public enum Format { EXCEL, CSV, PDF }
}
