package dev.pricemonitor.pricelist;

/** Файл прайса в том виде, в каком его прислал поставщик. */
public record SourceFile(String name, byte[] content) {
}
