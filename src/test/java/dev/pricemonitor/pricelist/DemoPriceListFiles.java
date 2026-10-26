package dev.pricemonitor.pricelist;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Генерирует демо-прайсы в {@code src/main/resources/demo/pricelists}: три выдуманных поставщика, две недели.
 * Нарочно "грязные": шапка над таблицей, группы, цены текстом, русские буквы в артикулах, цены без НДС,
 * повторы, пустые цены, название, переехавшее в PDF на две строки.
 *
 * <p>Запуск из корня проекта (нужен шрифт с кириллицей, по умолчанию Arial из Windows):
 * {@code java -cp "target/test-classes;target/classes;<зависимости>" dev.pricemonitor.pricelist.DemoPriceListFiles}
 */
public final class DemoPriceListFiles {

    private static final Path OUT = Path.of("src/main/resources/demo/pricelists");
    private static final String FONT = System.getProperty("font", "C:/Windows/Fonts/arial.ttf");

    /** Строка прайса за две недели; пустая цена — позиции в эту неделю нет. */
    private record Line(String group, String brand, String article, String name,
                        String prevPrice, String curPrice, String prevStock, String curStock) {
    }

    // Автодеталь-Опт: Excel, бренд отдельной колонкой, артикулы с пробелами, часть цен текстом.
    private static final List<Line> AVTODETAL = List.of(
            new Line("ФИЛЬТРЫ", "MANN-FILTER", "W 712/75", "Фильтр масляный", "18.90", "19.90", "есть", "есть"),
            new Line("ФИЛЬТРЫ", "MANN", "C 30 005", "Фильтр воздушный", "32.50", "32.50", "есть", "есть"),
            new Line("ФИЛЬТРЫ", "MANN-FILTER", "CUK 2939", "Фильтр салонный угольный", "41.00", "43.50", "есть", "есть"),
            new Line("ФИЛЬТРЫ", "BOSCH", "0 986 452 041", "Фильтр масляный", "16.40", "16.40", "есть", "нет"),
            new Line("ФИЛЬТРЫ", "MAHLE", "OC 90", "Фильтр масляный", "17.20", "17.20", "есть", "есть"),
            new Line("ТОРМОЗНАЯ СИСТЕМА", "BOSCH", "0 986 494 524", "Колодки тормозные передние", "86.00", "86.00", "под заказ", "под заказ"),
            new Line("ТОРМОЗНАЯ СИСТЕМА", "TRW", "GDB1550", "Колодки тормозные передние", "64.00", "64.00", "есть", "есть"),
            new Line("ТОРМОЗНАЯ СИСТЕМА", "TRW", "DF4263", "Диск тормозной передний", "98.00", "104.00", "есть", "есть"),
            new Line("ТОРМОЗНАЯ СИСТЕМА", "ATE", "13.0460-7184.2", "Колодки тормозные передние", "78.00", "78.00", "есть", "есть"),
            new Line("ТОРМОЗНАЯ СИСТЕМА", "BREMBO", "P 85 020", "Колодки тормозные передние", "", "92.00", "", "есть"),
            new Line("ЗАЖИГАНИЕ", "NGK", "BKR6E", "Свеча зажигания", "9.60", "9.60", "есть", "есть"),
            new Line("ЗАЖИГАНИЕ", "NGK", "ZFR6F-11", "Свеча зажигания", "11.20", "11.20", "есть", "есть"),
            new Line("ЗАЖИГАНИЕ", "BOSCH", "0 242 236 565", "Свеча зажигания", "12.80", "13.40", "есть", "есть"),
            new Line("СЦЕПЛЕНИЕ И ПРИВОД", "SACHS", "3000 951 018", "Комплект сцепления", "412,00 р.", "398,00 р.", "есть", "есть"),
            new Line("СЦЕПЛЕНИЕ И ПРИВОД", "LUK", "623 3087 00", "Комплект сцепления", "389.00", "389.00", "есть", "есть"),
            new Line("СЦЕПЛЕНИЕ И ПРИВОД", "GATES", "5PK1135", "Ремень поликлиновой", "28.40", "28.40", "есть", "есть"),
            new Line("СЦЕПЛЕНИЕ И ПРИВОД", "CONTITECH", "CT1028K2", "Комплект ГРМ", "165.00", "171.00", "есть", "есть"),
            new Line("ПОДВЕСКА", "SKF", "VKBA 3525", "Подшипник ступицы", "118.00", "118.00", "есть", "есть"),
            new Line("ПОДВЕСКА", "LEMFÖRDER", "25881 01", "Рычаг подвески", "156.00", "156.00", "есть", "есть"),
            new Line("ОХЛАЖДЕНИЕ", "FEBI BILSTEIN", "22381", "Термостат", "47.30", "47.30", "есть", "есть"),
            new Line("РАЗНОЕ", "BOSCH", "3 397 007 620", "Щётки стеклоочистителя Aerotwin", "54.00", "54.00", "есть", "есть"),
            new Line("МАСЛА", "CASTROL", "15665E", "Масло моторное EDGE 5W-30, 4 л", "129.00", "135.00", "есть", "есть"),
            new Line("МАСЛА", "MOBIL", "152566", "Масло моторное Super 3000 X1 5W-40, 4 л", "98.00", "98.00", "есть", "есть"),
            new Line("МАСЛА", "CASTROL", "15D6E1", "Масло трансмиссионное Syntrans 75W-90, 1 л", "по запросу", "по запросу", "есть", "есть"),
            new Line("АКЦИЯ НЕДЕЛИ", "NGK", "BKR 6E", "Свеча зажигания, цена по акции", "", "8.90", "", "есть"));

    // ПартсЛайн: выгрузка из 1С, цены без НДС, бренд только в названии, русские буквы в артикулах.
    private static final List<Line> PARTSLINE = List.of(
            new Line("", "", "W712/75", "Фильтр масляный MANN W712/75", "15,50", "15,50", "34", "30"),
            new Line("", "", "СUK2939", "Фильтр салонный угольный MANN CUK2939", "34,20", "34,20", "12", "9"),
            new Line("", "", "0986452041", "Фильтр масляный BOSCH", "13,10", "13,10", "25", "18"),
            new Line("", "", "0 986 494 524", "Колодки тормозные передние BOSCH", "70,00", "74,50", "6", "4"),
            new Line("", "", "0242236565", "Свеча зажигания BOSCH FR7DCX+", "10,50", "10,50", ">50", ">50"),
            new Line("", "", "ВКR6Е", "Свеча зажигания NGK", "7,90", "7,90", ">50", ">50"),
            new Line("", "", "GDВ1550", "Колодки тормозные передние TRW", "52,00", "49,90", "8", "11"),
            new Line("", "", "3000951018", "Комплект сцепления SACHS", "335,00", "335,00", "2", "0"),
            new Line("", "", "5РК1135", "Ремень поликлиновой GATES", "22,90", "22,90", "15", "15"),
            new Line("", "", "OP526", "Фильтр масляный FILTRON OP 526", "9,80", "9,80", "40", "40"),
            new Line("", "", "15665E", "Масло моторное CASTROL EDGE 5W-30 4л", "104,00", "108,00", "20", "14"),
            new Line("", "", "VKBA3525", "Подшипник ступицы SKF", "95,00", "", "3", ""),
            new Line("", "", "13046071842", "Колодки тормозные передние", "63,00", "63,00", "5", "5"),
            new Line("", "", "1987302777", "Лампа H7 Pure Light BOSCH", "", "6,20", "", "60"),
            new Line("", "", "", "Фильтр топливный BOSCH", "21,50", "21,50", "5", "5"),
            new Line("", "", "PU1058X", "Фильтр топливный MANN PU1058X", "звоните", "звоните", "0", "0"));

    // ТехноДеталь: PDF, артикулы без пробелов, наличие плюсом и минусом.
    private static final List<Line> TEHNODETAL = List.of(
            new Line("", "MANN", "W712/75", "Фильтр масляный", "19.40", "19.40", "+", "+"),
            new Line("", "MANN", "C30005", "Фильтр воздушный", "31.80", "33.20", "+", "+"),
            new Line("", "BOSCH", "0986452041", "Фильтр масляный", "16.90", "16.20", "+", "+"),
            new Line("", "BOSCH", "0986494524", "Колодки тормозные передние", "88.50", "88.50", "-", "+"),
            new Line("", "BOSCH", "3397007620", "Щётки стеклоочистителя Aerotwin", "52.90", "52.90", "+", "+"),
            new Line("", "NGK", "BKR6E", "Свеча зажигания", "9.90", "9.50", "+", "+"),
            new Line("", "NGK", "ZFR6F11", "Свеча зажигания", "11.50", "11.50", "+", "+"),
            new Line("", "TRW", "GDB1550", "Колодки тормозные передние", "66.00", "66.00", "+", "+"),
            new Line("", "TRW", "DF4263", "Диск тормозной передний", "101.50", "101.50", "+", "+"),
            new Line("", "FEBI", "22381", "Термостат", "46.80", "46.80", "+", "+"),
            new Line("", "LUK", "623308700", "Комплект сцепления", "379.00", "395.00", "заказ", "заказ"),
            new Line("", "GATES", "5PK1135", "Ремень поликлиновой", "29.10", "29.10", "+", "+"),
            new Line("", "CONTITECH", "CT1028K2", "Комплект ГРМ", "168.00", "168.00", "+", "+"),
            new Line("", "FILTRON", "OP526", "Фильтр масляный", "12.10", "12.10", "+", "+"),
            new Line("", "CASTROL", "15665E", "Масло моторное EDGE 5W-30 4 л", "131.00", "131.00", "+", "+"),
            new Line("", "MOBIL", "152566", "Масло моторное Super 3000 X1 5W-40 4 л", "96.50", "96.50", "+", "-"),
            new Line("", "KNECHT", "OC90", "Фильтр масляный", "16.80", "16.80", "+", "+"),
            new Line("", "LEMFORDER", "2588101", "Рычаг подвески", "159.00", "159.00", "+", "+"),
            new Line("", "BREMBO", "P85020", "Колодки тормозные передние", "95.00", "95.00", "+", "+"));

    private DemoPriceListFiles() {
    }

    public static void main(String[] args) throws IOException {
        for (boolean current : new boolean[] {false, true}) {
            String week = current ? DemoPriceLists.CURRENT : DemoPriceLists.PREVIOUS;
            String date = current ? "27.10.2026" : "20.10.2026";
            String suffix = current ? "2026-10-27" : "2026-10-20";
            Path dir = OUT.resolve(week);
            Files.createDirectories(dir);
            avtodetal(dir.resolve("avtodetal-" + suffix + ".xlsx"), date, current);
            partsline(dir.resolve("partsline-" + suffix + ".csv"), date, current);
            tehnodetal(dir.resolve("tehnodetal-" + suffix + ".pdf"), date, current);
        }
    }

    private static void avtodetal(Path file, String date, boolean current) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = book.createSheet("Прайс");
            Font bold = book.createFont();
            bold.setBold(true);
            CellStyle title = book.createCellStyle();
            title.setFont(bold);
            CellStyle header = book.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            CellStyle group = book.createCellStyle();
            group.setFont(bold);
            group.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            group.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = book.createCellStyle();
            money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));

            sheet.createRow(0).createCell(0).setCellValue("ООО Автодеталь-Опт");
            sheet.getRow(0).getCell(0).setCellStyle(title);
            sheet.createRow(1).createCell(0).setCellValue("Прайс-лист от " + date);
            sheet.createRow(2).createCell(0).setCellValue("г. Минск, тел. +375 (17) 000-00-00, opt@example.by");
            String[] titles = {"№", "Производитель", "Артикул", "Наименование", "Цена, руб.", "Наличие"};
            Row head = sheet.createRow(4);
            for (int i = 0; i < titles.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(titles[i]);
                cell.setCellStyle(header);
            }
            int r = 5;
            int number = 1;
            String lastGroup = null;
            for (Line line : AVTODETAL) {
                String price = current ? line.curPrice() : line.prevPrice();
                if (price.isEmpty()) {
                    continue;
                }
                if (!line.group().equals(lastGroup)) {
                    Row groupRow = sheet.createRow(r);
                    Cell cell = groupRow.createCell(0);
                    cell.setCellValue(line.group());
                    cell.setCellStyle(group);
                    sheet.addMergedRegion(new CellRangeAddress(r, r, 0, titles.length - 1));
                    r++;
                    lastGroup = line.group();
                }
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(number++);
                row.createCell(1).setCellValue(line.brand());
                row.createCell(2).setCellValue(line.article());
                row.createCell(3).setCellValue(line.name());
                Cell priceCell = row.createCell(4);
                if (price.matches("\\d+\\.\\d{2}")) {
                    priceCell.setCellValue(Double.parseDouble(price));
                    priceCell.setCellStyle(money);
                } else {
                    priceCell.setCellValue(price);
                }
                row.createCell(5).setCellValue(current ? line.curStock() : line.prevStock());
            }
            r++;
            sheet.createRow(r).createCell(0).setCellValue("Цены указаны в белорусских рублях с НДС.");
            int[] widths = {5, 16, 18, 42, 12, 12};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            book.write(out);
        }
    }

    private static void partsline(Path file, String date, boolean current) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("Выгрузка остатков " + date + " 10:15;;;");
        lines.add("Код;Номенклатура;Цена без НДС, руб.;Остаток");
        for (Line line : PARTSLINE) {
            String price = current ? line.curPrice() : line.prevPrice();
            if (price.isEmpty()) {
                continue;
            }
            lines.add(String.join(";", line.article(), line.name(), price, current ? line.curStock() : line.prevStock()));
        }
        Files.writeString(file, String.join("\r\n", lines) + "\r\n", Charset.forName("windows-1251"));
    }

    private static void tehnodetal(Path file, String date, boolean current) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType0Font font = PDType0Font.load(document, Path.of(FONT).toFile());
            float y = 790;
            try (PDPageContentStream text = new PDPageContentStream(document, page)) {
                write(text, font, 14, 40, y, "ТехноДеталь");
                y -= 18;
                write(text, font, 10, 40, y, "Прайс-лист на " + date);
                y -= 28;
                row(text, font, y, "Артикул", "Бренд", "Наименование", "Цена", "Наличие");
                y -= 16;
                for (Line line : TEHNODETAL) {
                    String price = current ? line.curPrice() : line.prevPrice();
                    if (price.isEmpty()) {
                        continue;
                    }
                    row(text, font, y, line.article(), line.brand(), line.name(), price,
                            current ? line.curStock() : line.prevStock());
                    y -= 14;
                }
                if (current) {
                    // Длинное название переехало на вторую строку: так бывает в PDF из 1С.
                    row(text, font, y, "13.0460-7184.2", "ATE", "Колодки тормозные передние", "", "");
                    y -= 12;
                    row(text, font, y, "", "", "(комплект на ось, 4 шт.)", "76.90", "+");
                    y -= 14;
                }
                y -= 14;
                write(text, font, 9, 40, y, "Цены с НДС, BYN. Страница 1 из 1");
            }
            document.save(file.toFile());
        }
    }

    private static void row(PDPageContentStream text, PDType0Font font, float y, String article, String brand,
                            String name, String price, String stock) throws IOException {
        write(text, font, 9, 40, y, article);
        write(text, font, 9, 130, y, brand);
        write(text, font, 9, 205, y, name);
        if (!price.isEmpty()) {
            write(text, font, 9, 470 - font.getStringWidth(price) / 1000 * 9, y, price);
        }
        write(text, font, 9, 500, y, stock);
    }

    private static void write(PDPageContentStream text, PDType0Font font, float size, float x, float y, String value)
            throws IOException {
        if (value.isEmpty()) {
            return;
        }
        text.beginText();
        text.setFont(font, size);
        text.newLineAtOffset(x, y);
        text.showText(value);
        text.endText();
    }
}
