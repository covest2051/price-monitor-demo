package dev.pricemonitor.pricelist;

import dev.pricemonitor.pricelist.PriceListProperties.Supplier;
import dev.pricemonitor.pricelist.PriceListReport.SupplierSummary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Отчёт по прайсам в Excel: сводка, единый каталог, изменения, проблемные строки. */
@Component
public class PriceListExcelExporter {

    public byte[] export(PriceListReport report) {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(book);
            summary(book.createSheet("Сводка"), report, styles);
            catalog(book.createSheet("Каталог"), report, styles);
            changes(book.createSheet("Изменения"), report, styles);
            problems(book.createSheet("Проблемы"), report, styles);
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать Excel", e);
        }
    }

    private void summary(Sheet sheet, PriceListReport report, Styles styles) {
        header(sheet, styles, "Поставщик", "Файл", "Прошлый файл", "Позиций", "Проблемных строк", "Подорожало",
                "Подешевело", "Закончилось", "Снова в наличии", "Новые", "Пропали");
        int r = 1;
        for (SupplierSummary summary : report.summaries()) {
            Row line = sheet.createRow(r++);
            String code = summary.supplier().code();
            line.createCell(0).setCellValue(summary.supplier().name());
            line.createCell(1).setCellValue(summary.fileName());
            line.createCell(2).setCellValue(summary.previousFileName() != null ? summary.previousFileName() : "нет");
            line.createCell(3).setCellValue(summary.offers());
            line.createCell(4).setCellValue(summary.problems());
            line.createCell(5).setCellValue(report.count(Change.Type.PRICE_UP, code));
            line.createCell(6).setCellValue(report.count(Change.Type.PRICE_DOWN, code));
            line.createCell(7).setCellValue(report.count(Change.Type.OUT_OF_STOCK, code));
            line.createCell(8).setCellValue(report.count(Change.Type.BACK_IN_STOCK, code));
            line.createCell(9).setCellValue(report.count(Change.Type.NEW, code));
            line.createCell(10).setCellValue(report.count(Change.Type.REMOVED, code));
        }
        r++;
        sheet.createRow(r++).createCell(0).setCellValue("Товаров в каталоге: " + report.catalog().size()
                + ", из них у нескольких поставщиков: " + report.itemsAtSeveralSuppliers());
        sheet.createRow(r).createCell(0).setCellValue("Все цены с НДС; цены поставщиков без НДС пересчитаны.");
        widths(sheet, 22, 30, 30, 10, 12, 12, 12, 12, 12, 10, 10);
    }

    private void catalog(Sheet sheet, PriceListReport report, Styles styles) {
        int supplierCount = report.suppliers().size();
        String[] titles = new String[3 + supplierCount * 2 + 3];
        titles[0] = "Бренд";
        titles[1] = "Артикул";
        titles[2] = "Наименование";
        for (int i = 0; i < supplierCount; i++) {
            titles[3 + i * 2] = report.suppliers().get(i).name() + ", цена";
            titles[4 + i * 2] = report.suppliers().get(i).name() + ", наличие";
        }
        int bestColumn = 3 + supplierCount * 2;
        titles[bestColumn] = "Лучшая цена (в наличии)";
        titles[bestColumn + 1] = "У кого";
        titles[bestColumn + 2] = "Разброс цен";
        header(sheet, styles, titles);
        int r = 1;
        for (CatalogItem item : report.catalog()) {
            Row line = sheet.createRow(r++);
            line.createCell(0).setCellValue(item.brand());
            line.createCell(1).setCellValue(item.article());
            line.createCell(2).setCellValue(item.name());
            for (int i = 0; i < supplierCount; i++) {
                Offer offer = item.offer(report.suppliers().get(i).code());
                if (offer != null) {
                    money(line, 3 + i * 2, offer.price(), item.isBest(offer) ? styles.best : styles.money);
                    line.createCell(4 + i * 2).setCellValue(offer.stock().text());
                }
            }
            Offer best = item.best();
            if (best != null) {
                money(line, bestColumn, best.price(), styles.money);
                line.createCell(bestColumn + 1).setCellValue(supplierName(report, best.supplierCode()));
            } else {
                line.createCell(bestColumn + 1).setCellValue("нет в наличии");
            }
            money(line, bestColumn + 2, item.spread(), styles.money);
        }
        int[] widths = new int[titles.length];
        widths[0] = 14;
        widths[1] = 18;
        widths[2] = 40;
        for (int i = 0; i < supplierCount; i++) {
            widths[3 + i * 2] = 14;
            widths[4 + i * 2] = 14;
        }
        widths[bestColumn] = 14;
        widths[bestColumn + 1] = 18;
        widths[bestColumn + 2] = 12;
        widths(sheet, widths);
    }

    private void changes(Sheet sheet, PriceListReport report, Styles styles) {
        header(sheet, styles, "Что изменилось", "Поставщик", "Бренд", "Артикул", "Наименование", "Было", "Стало",
                "Изменение", "Изменение, %", "Наличие было", "Наличие стало");
        int r = 1;
        for (Change change : report.changes()) {
            Row line = sheet.createRow(r++);
            line.createCell(0).setCellValue(change.type().title());
            line.createCell(1).setCellValue(change.supplierName());
            line.createCell(2).setCellValue(change.brand());
            line.createCell(3).setCellValue(change.article());
            line.createCell(4).setCellValue(change.name());
            money(line, 5, change.oldPrice(), styles.money);
            money(line, 6, change.newPrice(), styles.money);
            BigDecimal diff = change.diff();
            CellStyle diffStyle = diff == null || diff.signum() == 0 ? styles.money
                    : diff.signum() > 0 ? styles.up : styles.down;
            money(line, 7, diff, diffStyle);
            if (change.percent() != null) {
                Cell percent = line.createCell(8);
                percent.setCellValue(change.percent().doubleValue());
                percent.setCellStyle(styles.percent);
            }
            line.createCell(9).setCellValue(change.oldStock() != null ? change.oldStock().text() : "");
            line.createCell(10).setCellValue(change.newStock() != null ? change.newStock().text() : "");
        }
        widths(sheet, 18, 18, 14, 18, 40, 12, 12, 12, 13, 14, 14);
    }

    private void problems(Sheet sheet, PriceListReport report, Styles styles) {
        header(sheet, styles, "Поставщик", "Файл", "Строка", "Что не так", "Содержимое строки");
        int r = 1;
        for (Problem problem : report.problems()) {
            Row line = sheet.createRow(r++);
            line.createCell(0).setCellValue(problem.supplierName());
            line.createCell(1).setCellValue(problem.fileName());
            if (problem.line() != null) {
                line.createCell(2).setCellValue(problem.line());
            }
            line.createCell(3).setCellValue(problem.reason());
            line.createCell(4).setCellValue(problem.content());
        }
        widths(sheet, 18, 30, 8, 55, 70);
    }

    private static String supplierName(PriceListReport report, String code) {
        return report.suppliers().stream().filter(s -> s.code().equals(code)).map(Supplier::name).findFirst().orElse(code);
    }

    private static void header(Sheet sheet, Styles styles, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.header);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, titles.length - 1));
    }

    private static void money(Row row, int column, BigDecimal value, CellStyle style) {
        if (value != null) {
            Cell cell = row.createCell(column);
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private static void widths(Sheet sheet, int... chars) {
        for (int i = 0; i < chars.length; i++) {
            sheet.setColumnWidth(i, chars[i] * 256);
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle money;
        final CellStyle best;
        final CellStyle up;
        final CellStyle down;
        final CellStyle percent;

        Styles(XSSFWorkbook book) {
            Font bold = book.createFont();
            bold.setBold(true);
            header = book.createCellStyle();
            header.setFont(bold);
            header.setWrapText(true);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            short moneyFormat = book.createDataFormat().getFormat("#,##0.00");
            money = book.createCellStyle();
            money.setDataFormat(moneyFormat);
            best = book.createCellStyle();
            best.setDataFormat(moneyFormat);
            best.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            best.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font red = book.createFont();
            red.setColor(IndexedColors.RED.getIndex());
            up = book.createCellStyle();
            up.setDataFormat(book.createDataFormat().getFormat("+#,##0.00;-#,##0.00"));
            up.setFont(red);
            Font green = book.createFont();
            green.setColor(IndexedColors.GREEN.getIndex());
            down = book.createCellStyle();
            down.setDataFormat(book.createDataFormat().getFormat("+#,##0.00;-#,##0.00"));
            down.setFont(green);
            percent = book.createCellStyle();
            percent.setDataFormat(book.createDataFormat().getFormat("+0.0;-0.0"));
        }
    }
}
