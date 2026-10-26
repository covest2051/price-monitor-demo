package dev.pricemonitor.pricelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.pricemonitor.pricelist.Change.Type;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Полный прогон на демо-прайсах: Excel с группами, CSV из 1С без НДС, PDF — за две недели. */
class PriceListServiceTest {

    private static PriceListReport report;

    @BeforeAll
    static void processDemoPriceLists() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("pricelist", new ClassPathResource("pricelist.yml"));
        PriceListProperties props = new Binder(ConfigurationPropertySources.from(yaml))
                .bind("pricelist", PriceListProperties.class)
                .get();
        DemoPriceLists demo = new DemoPriceLists();
        report = new PriceListService(props)
                .process(demo.files(DemoPriceLists.PREVIOUS), demo.files(DemoPriceLists.CURRENT));
    }

    @Test
    void sameProductFromThreeSuppliersBecomesOneRowWithPricesIncludingVat() {
        CatalogItem filter = item("MANN-FILTER W71275");

        assertThat(filter.offers()).containsOnlyKeys("avtodetal", "partsline", "tehnodetal");
        assertThat(filter.offer("avtodetal").price()).isEqualByComparingTo("19.90");
        // 15,50 без НДС
        assertThat(filter.offer("partsline").price()).isEqualByComparingTo("18.60");
        assertThat(filter.offer("tehnodetal").price()).isEqualByComparingTo("19.40");
        assertThat(filter.best().supplierCode()).isEqualTo("partsline");
    }

    @Test
    void articlesWithSpacesDotsAndCyrillicLettersAreMatched() {
        // "ВКR6Е" в CSV набран русскими В и Е
        assertThat(item("NGK BKR6E").offers()).containsOnlyKeys("avtodetal", "partsline", "tehnodetal");
        // "623 3087 00" и "623308700"
        assertThat(item("LUK 623308700").offers()).containsOnlyKeys("avtodetal", "tehnodetal");
        // "LEMFÖRDER" и "LEMFORDER"
        assertThat(item("LEMFORDER 2588101").offers()).containsOnlyKeys("avtodetal", "tehnodetal");
    }

    @Test
    void offerWithoutBrandJoinsProductWithSameArticle() {
        assertThat(item("ATE 13046071842").offers()).containsOnlyKeys("avtodetal", "partsline");
    }

    @Test
    void reportsChangesAgainstPreviousPriceLists() {
        assertThat(report.changes())
                .extracting(Change::type, Change::supplierCode, Change::article)
                .contains(
                        tuple(Type.PRICE_UP, "avtodetal", "DF4263"),
                        tuple(Type.PRICE_DOWN, "partsline", "GDB1550"),
                        tuple(Type.OUT_OF_STOCK, "partsline", "3000 951 018"),
                        tuple(Type.BACK_IN_STOCK, "tehnodetal", "0 986 494 524"),
                        tuple(Type.NEW, "avtodetal", "P 85 020"),
                        tuple(Type.REMOVED, "partsline", "VKBA 3525"));
        Change disc = report.changes().stream()
                .filter(c -> c.type() == Type.PRICE_UP && c.article().equals("DF4263") && c.supplierCode().equals("avtodetal"))
                .findFirst()
                .orElseThrow();
        assertThat(disc.diff()).isEqualByComparingTo("6.00");
        assertThat(disc.percent()).isEqualByComparingTo(new BigDecimal("6.1"));
    }

    @Test
    void rowsThatCannotBeParsedAreReportedNotLost() {
        assertThat(report.problems())
                .extracting(Problem::supplierName, Problem::reason)
                .anySatisfy(p -> assertThat(p.toList()).containsExactly("Автодеталь-Опт",
                        "Не распознана цена: \"по запросу\""))
                .anySatisfy(p -> assertThat((String) p.toList().get(1)).startsWith("Повтор"))
                .anySatisfy(p -> assertThat(p.toList()).containsExactly("ПартсЛайн", "Нет артикула"))
                .anySatisfy(p -> assertThat(p.toList()).containsExactly("ТехноДеталь", "Строка не похожа на позицию прайса"));
        assertThat(report.problems()).allSatisfy(problem -> assertThat(problem.line()).isNotNull());
    }

    @Test
    void exportsFourSheetsWithWholeCatalog() throws IOException {
        byte[] excel = new PriceListExcelExporter().export(report);

        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(4);
            assertThat(book.getSheet("Каталог").getLastRowNum()).isEqualTo(report.catalog().size());
            assertThat(book.getSheet("Изменения").getLastRowNum()).isEqualTo(report.changes().size());
        }
    }

    private static CatalogItem item(String key) {
        return report.catalog().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет в каталоге: " + key + ", есть: "
                        + report.catalog().stream().map(CatalogItem::key).toList()));
    }
}
