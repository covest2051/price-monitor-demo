package dev.pricemonitor.pricelist;

import dev.pricemonitor.pricelist.PriceListProperties.Columns;
import dev.pricemonitor.pricelist.PriceListProperties.Supplier;
import dev.pricemonitor.scrape.PriceParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** Разбирает один файл поставщика по его профилю: позиции в общем виде и строки, которые разобрать не удалось. */
class SupplierFileParser {

    /** Сколько строк сверху просматривать в поисках заголовка таблицы: выше него шапка с реквизитами. */
    private static final int HEADER_SEARCH_ROWS = 30;

    private final ArticleKeys keys;
    private final BigDecimal vatMultiplier;

    SupplierFileParser(ArticleKeys keys, BigDecimal vatPercent) {
        this.keys = keys;
        this.vatMultiplier = BigDecimal.ONE.add(vatPercent.movePointLeft(2));
    }

    record Parsed(Supplier supplier, String fileName, List<Offer> offers, List<Problem> problems) {
    }

    private enum Field { ARTICLE, BRAND, NAME, PRICE, STOCK }

    /** Строка в общем виде до проверки: все значения — текст как в файле. */
    private record RawRow(int line, String category, Map<Field, String> values, String content) {

        String get(Field field) {
            return values.getOrDefault(field, "");
        }
    }

    private record TableRow(int line, List<String> cells) {

        boolean isBlank() {
            return cells.stream().allMatch(String::isBlank);
        }

        String cell(Integer column) {
            return column != null && column < cells.size() ? cells.get(column) : "";
        }

        String content() {
            return String.join(" | ", cells.stream().filter(c -> !c.isBlank()).toList());
        }
    }

    Parsed parse(Supplier supplier, SourceFile file) {
        List<Problem> problems = new ArrayList<>();
        List<RawRow> rows;
        try {
            rows = switch (supplier.format()) {
                case EXCEL -> fromTable(supplier, file, excel(file.content()), problems);
                case CSV -> fromTable(supplier, file, csv(file.content(), supplier), problems);
                case PDF -> fromPdf(supplier, file, problems);
            };
        } catch (IOException | RuntimeException e) {
            problems.add(new Problem(supplier.name(), file.name(), null, "Файл не открывается: " + e.getMessage(), ""));
            return new Parsed(supplier, file.name(), List.of(), problems);
        }
        return new Parsed(supplier, file.name(), toOffers(supplier, file, rows, problems), problems);
    }

    private List<Offer> toOffers(Supplier supplier, SourceFile file, List<RawRow> rows, List<Problem> problems) {
        List<Offer> offers = new ArrayList<>();
        Map<String, Offer> seen = new HashMap<>();
        for (RawRow row : rows) {
            String article = row.get(Field.ARTICLE).trim();
            String articleKey = ArticleKeys.normalize(article);
            Optional<BigDecimal> price = PriceParser.parse(row.get(Field.PRICE)).filter(p -> p.signum() > 0);
            if (articleKey.isEmpty()) {
                problems.add(problem(supplier, file, row, "Нет артикула"));
                continue;
            }
            if (price.isEmpty()) {
                problems.add(problem(supplier, file, row, "Не распознана цена: \"" + row.get(Field.PRICE) + "\""));
                continue;
            }
            String name = row.get(Field.NAME).trim();
            String brand = row.get(Field.BRAND).isBlank() ? keys.brandInName(name) : keys.brand(row.get(Field.BRAND));
            Offer offer = new Offer(supplier.code(), file.name(), row.line(), row.category(), brand, article, articleKey,
                    name, withVat(price.get(), supplier), Stock.parse(row.get(Field.STOCK)));
            Offer first = seen.putIfAbsent(offer.key(), offer);
            if (first != null) {
                problems.add(problem(supplier, file, row, "Повтор: этот товар уже есть в строке " + first.line()
                        + " (цена " + first.price().toPlainString() + "), строка пропущена"));
                continue;
            }
            offers.add(offer);
        }
        return offers;
    }

    private BigDecimal withVat(BigDecimal price, Supplier supplier) {
        return supplier.pricesWithVat()
                ? price.setScale(2, RoundingMode.HALF_UP)
                : price.multiply(vatMultiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private static Problem problem(Supplier supplier, SourceFile file, RawRow row, String reason) {
        return new Problem(supplier.name(), file.name(), row.line(), reason, row.content());
    }

    // --- Таблицы: Excel и CSV ---

    private static List<RawRow> fromTable(Supplier supplier, SourceFile file, List<TableRow> table,
                                          List<Problem> problems) {
        int headerIndex = -1;
        Map<Field, Integer> columns = Map.of();
        for (int i = 0; i < Math.min(table.size(), HEADER_SEARCH_ROWS) && headerIndex < 0; i++) {
            Map<Field, Integer> found = columns(table.get(i), supplier.columns());
            if (found.containsKey(Field.ARTICLE) && found.containsKey(Field.PRICE)) {
                headerIndex = i;
                columns = found;
            }
        }
        if (headerIndex < 0) {
            problems.add(new Problem(supplier.name(), file.name(), null,
                    "Не найдена строка заголовков с колонками " + supplier.columns().article() + " и "
                            + supplier.columns().price(), ""));
            return List.of();
        }
        List<RawRow> rows = new ArrayList<>();
        String category = "";
        for (TableRow row : table.subList(headerIndex + 1, table.size())) {
            if (row.isBlank()) {
                continue;
            }
            Map<Field, String> values = new EnumMap<>(Field.class);
            columns.forEach((field, column) -> values.put(field, row.cell(column)));
            if (values.get(Field.ARTICLE).isBlank() && values.get(Field.PRICE).isBlank()) {
                // Строка-группа ("ФИЛЬТРЫ") или подпись под таблицей: запоминаем как раздел.
                category = row.content();
                continue;
            }
            rows.add(new RawRow(row.line(), category, values, row.content()));
        }
        return rows;
    }

    private static Map<Field, Integer> columns(TableRow row, Columns titles) {
        Map<Field, List<String>> wanted = Map.of(Field.ARTICLE, titles.article(), Field.BRAND, titles.brand(),
                Field.NAME, titles.name(), Field.PRICE, titles.price(), Field.STOCK, titles.stock());
        Map<Field, Integer> found = new EnumMap<>(Field.class);
        for (int c = 0; c < row.cells().size(); c++) {
            String header = header(row.cells().get(c));
            for (Map.Entry<Field, List<String>> entry : wanted.entrySet()) {
                if (!found.containsKey(entry.getKey())
                        && entry.getValue().stream().anyMatch(title -> header(title).equals(header))) {
                    found.put(entry.getKey(), c);
                }
            }
        }
        return found;
    }

    private static String header(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").trim()
                .replaceAll("[.:]$", "");
    }

    private static List<TableRow> excel(byte[] content) throws IOException {
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = book.getSheetAt(0);
            List<TableRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                    cells.add(text(row.getCell(c)));
                }
                rows.add(new TableRow(row.getRowNum() + 1, cells));
            }
            return rows;
        }
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** CSV с кавычками по правилам Excel; поля с переносом строки внутри кавычек не поддерживаются. */
    private static List<TableRow> csv(byte[] content, Supplier supplier) {
        String text = new String(content, supplier.charset());
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\r?\n", -1);
        List<TableRow> rows = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            rows.add(new TableRow(i + 1, splitCsv(lines[i], supplier.delimiter().charAt(0))));
        }
        return rows;
    }

    private static List<String> splitCsv(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(ch);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    // --- PDF с текстовым слоем ---

    /**
     * Текст PDF разбирается построчно регулярным выражением из профиля. Строки до первой позиции — шапка;
     * после неё каждая непустая строка должна быть позицией или подходить под {@code skip-pattern}
     * (повтор заголовка, номер страницы), иначе попадает в проблемы: так видно, если название переехало на две строки.
     */
    private static List<RawRow> fromPdf(Supplier supplier, SourceFile file, List<Problem> problems)
            throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(file.content())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }
        Pattern item = Pattern.compile(supplier.linePattern());
        Pattern skip = supplier.skipPattern() != null ? Pattern.compile(supplier.skipPattern()) : null;
        List<RawRow> rows = new ArrayList<>();
        boolean tableStarted = false;
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            Matcher matcher = item.matcher(line);
            if (matcher.matches()) {
                tableStarted = true;
                Map<Field, String> values = new EnumMap<>(Field.class);
                for (Field field : Field.values()) {
                    values.put(field, group(matcher, field.name().toLowerCase(Locale.ROOT)));
                }
                rows.add(new RawRow(i + 1, "", values, line));
            } else if (tableStarted && !line.isEmpty() && (skip == null || !skip.matcher(line).find())) {
                problems.add(new Problem(supplier.name(), file.name(), i + 1,
                        "Строка не похожа на позицию прайса", line));
            }
        }
        return rows;
    }

    private static String group(Matcher matcher, String name) {
        try {
            String value = matcher.group(name);
            return value != null ? value : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
