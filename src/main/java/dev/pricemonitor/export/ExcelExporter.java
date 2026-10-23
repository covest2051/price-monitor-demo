package dev.pricemonitor.export;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.product.DashboardService;
import dev.pricemonitor.product.PriceCheckEntity;
import dev.pricemonitor.product.ProductEntity;
import dev.pricemonitor.product.ProductRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Выгрузка: лист с текущими ценами и лист с историей проверок за период. */
@Component
public class ExcelExporter {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DashboardService dashboard;
    private final MonitorProperties props;

    public ExcelExporter(DashboardService dashboard, MonitorProperties props) {
        this.dashboard = dashboard;
        this.props = props;
    }

    public byte[] export() {
        List<ProductRow> rows = dashboard.rows();
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(book);
            currentPrices(book.createSheet("Цены"), rows, styles);
            history(book.createSheet("История " + props.historyDays() + " дней"), rows, styles);
            book.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сформировать Excel", e);
        }
    }

    private void currentPrices(Sheet sheet, List<ProductRow> rows, Styles styles) {
        header(sheet, styles, "Товар", "Сайт", "Цена", "Было", "Изменение", "Изменение, %", "Наличие",
                "Проверено", "Ошибка", "Ссылка");
        int r = 1;
        for (ProductRow row : rows) {
            Row line = sheet.createRow(r++);
            line.createCell(0).setCellValue(row.product().getDisplayName());
            line.createCell(1).setCellValue(row.siteName());
            money(line, 2, row.price(), styles);
            money(line, 3, row.previousPrice(), styles);
            money(line, 4, row.change(), styles);
            number(line, 5, row.changePercent());
            line.createCell(6).setCellValue(availability(row.available()));
            line.createCell(7).setCellValue(when(row.lastCheckedAt()));
            line.createCell(8).setCellValue(row.lastError() != null ? row.lastError() : "");
            line.createCell(9).setCellValue(row.product().getUrl());
        }
        widths(sheet, 45, 20, 12, 12, 12, 14, 14, 18, 40, 60);
    }

    private void history(Sheet sheet, List<ProductRow> rows, Styles styles) {
        header(sheet, styles, "Товар", "Проверено", "Цена", "Наличие", "Статус", "Ошибка");
        Map<Long, ProductEntity> byId = rows.stream()
                .collect(Collectors.toMap(row -> row.product().getId(), ProductRow::product, (a, b) -> a));
        int r = 1;
        for (PriceCheckEntity check : dashboard.historyOfAll()) {
            ProductEntity product = byId.get(check.getProductId());
            if (product == null) {
                continue;
            }
            Row line = sheet.createRow(r++);
            line.createCell(0).setCellValue(product.getDisplayName());
            line.createCell(1).setCellValue(when(check.getCheckedAt()));
            money(line, 2, check.getPrice(), styles);
            line.createCell(3).setCellValue(check.isOk() ? availability(check.getAvailable()) : "");
            line.createCell(4).setCellValue(check.isOk() ? "OK" : "Ошибка");
            line.createCell(5).setCellValue(check.getError() != null ? check.getError() : "");
        }
        widths(sheet, 45, 18, 12, 14, 10, 60);
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

    private static void money(Row row, int column, BigDecimal value, Styles styles) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(styles.money);
        }
    }

    private static void number(Row row, int column, BigDecimal value) {
        if (value != null) {
            row.createCell(column).setCellValue(value.doubleValue());
        }
    }

    private static void widths(Sheet sheet, int... chars) {
        for (int i = 0; i < chars.length; i++) {
            sheet.setColumnWidth(i, chars[i] * 256);
        }
    }

    private String when(Instant instant) {
        return instant == null ? "" : WHEN.format(instant.atZone(props.zone()));
    }

    private static String availability(Boolean available) {
        return available == null ? "" : available ? "В наличии" : "Нет в наличии";
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle money;

        Styles(XSSFWorkbook book) {
            Font bold = book.createFont();
            bold.setBold(true);
            header = book.createCellStyle();
            header.setFont(bold);
            money = book.createCellStyle();
            money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
        }
    }
}
