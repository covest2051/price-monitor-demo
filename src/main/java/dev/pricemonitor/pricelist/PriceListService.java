package dev.pricemonitor.pricelist;

import dev.pricemonitor.pricelist.PriceListProperties.Supplier;
import dev.pricemonitor.pricelist.PriceListReport.SupplierSummary;
import dev.pricemonitor.pricelist.SupplierFileParser.Parsed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Сводит прайсы поставщиков в единый каталог и сравнивает с прошлыми прайсами.
 * Работает целиком в памяти: на вход файлы, на выход отчёт; хранить прошлые прайсы — забота вызывающего.
 */
@Service
public class PriceListService {

    private final PriceListProperties props;
    private final SupplierFileParser parser;

    public PriceListService(PriceListProperties props) {
        this.props = props;
        this.parser = new SupplierFileParser(new ArticleKeys(props.brands()), props.vatPercent());
    }

    public PriceListReport process(List<SourceFile> previous, List<SourceFile> current) {
        List<Problem> problems = new ArrayList<>();
        Map<String, Parsed> now = parseAll(current, problems);
        // Проблемы прошлых прайсов уже были в прошлом отчёте, повторять их не нужно.
        Map<String, Parsed> before = parseAll(previous, new ArrayList<>());

        List<CatalogItem> catalog = CatalogItem.build(offers(now));
        Map<String, String> compared = new LinkedHashMap<>();
        now.values().stream()
                .filter(parsed -> before.containsKey(parsed.supplier().code()))
                .forEach(parsed -> compared.put(parsed.supplier().code(), parsed.supplier().name()));
        List<Change> changes = Change.compare(CatalogItem.build(offers(before)), catalog, compared);

        List<SupplierSummary> summaries = now.values().stream()
                .map(parsed -> new SupplierSummary(parsed.supplier(), parsed.fileName(),
                        before.containsKey(parsed.supplier().code()) ? before.get(parsed.supplier().code()).fileName() : null,
                        parsed.offers().size(), parsed.problems().size()))
                .toList();
        now.values().forEach(parsed -> problems.addAll(parsed.problems()));
        List<Supplier> suppliers = now.values().stream().map(Parsed::supplier).toList();
        return new PriceListReport(suppliers, catalog, changes, problems, summaries);
    }

    /** Файлы разбираются в порядке профилей поставщиков, чтобы колонки отчёта не зависели от порядка загрузки. */
    private Map<String, Parsed> parseAll(List<SourceFile> files, List<Problem> problems) {
        Map<String, Parsed> bySupplier = new LinkedHashMap<>();
        List<SourceFile> sorted = files.stream().sorted(Comparator.comparing(SourceFile::name)).toList();
        for (Supplier supplier : props.suppliers()) {
            for (SourceFile file : sorted) {
                if (!supplier.matches(file.name())) {
                    continue;
                }
                if (bySupplier.containsKey(supplier.code())) {
                    problems.add(new Problem(supplier.name(), file.name(), null,
                            "Второй файл того же поставщика пропущен, взят " + bySupplier.get(supplier.code()).fileName(), ""));
                    continue;
                }
                bySupplier.put(supplier.code(), parser.parse(supplier, file));
            }
        }
        for (SourceFile file : sorted) {
            if (props.suppliers().stream().noneMatch(supplier -> supplier.matches(file.name()))) {
                problems.add(new Problem("—", file.name(), null,
                        "Поставщик не определён: имя файла не подходит ни под один профиль", ""));
            }
        }
        return bySupplier;
    }

    private static List<Offer> offers(Map<String, Parsed> parsed) {
        return parsed.values().stream().flatMap(p -> p.offers().stream()).toList();
    }
}
