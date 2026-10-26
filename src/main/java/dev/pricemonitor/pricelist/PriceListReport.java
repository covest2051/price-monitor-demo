package dev.pricemonitor.pricelist;

import dev.pricemonitor.pricelist.PriceListProperties.Supplier;
import java.util.List;

/** Результат обработки: каталог, изменения к прошлым прайсам, проблемные строки и сводка по поставщикам. */
public record PriceListReport(
        List<Supplier> suppliers,
        List<CatalogItem> catalog,
        List<Change> changes,
        List<Problem> problems,
        List<SupplierSummary> summaries) {

    /**
     * @param previousFileName null, если прошлого прайса не было: тогда изменения по поставщику не считаются
     */
    public record SupplierSummary(Supplier supplier, String fileName, String previousFileName, int offers,
                                  long problems) {
    }

    public long count(Change.Type type) {
        return changes.stream().filter(change -> change.type() == type).count();
    }

    public long count(Change.Type type, String supplierCode) {
        return changes.stream().filter(c -> c.type() == type && c.supplierCode().equals(supplierCode)).count();
    }

    public long itemsAtSeveralSuppliers() {
        return catalog.stream().filter(item -> item.offers().size() > 1).count();
    }
}
