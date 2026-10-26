package dev.pricemonitor.pricelist;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Прайсы трёх выдуманных поставщиков автозапчастей за две недели: Excel с шапкой и группами, CSV из 1С без НДС,
 * PDF. Лежат в {@code resources/demo/pricelists}, пересоздаются {@code DemoPriceListFiles} из тестов.
 */
@Component
public class DemoPriceLists {

    public static final String PREVIOUS = "previous";
    public static final String CURRENT = "current";

    public List<SourceFile> files(String week) {
        if (!PREVIOUS.equals(week) && !CURRENT.equals(week)) {
            return List.of();
        }
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:demo/pricelists/" + week + "/*");
            return Arrays.stream(resources)
                    .filter(resource -> resource.getFilename() != null)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .map(DemoPriceLists::read)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SourceFile> file(String week, String name) {
        return files(week).stream().filter(file -> file.name().equals(name)).findFirst();
    }

    private static SourceFile read(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return new SourceFile(resource.getFilename(), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
