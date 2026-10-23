package dev.pricemonitor.product;

import dev.pricemonitor.site.Site;
import dev.pricemonitor.site.SiteRegistry;
import java.net.URI;
import java.time.Clock;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final SiteRegistry sites;
    private final Clock clock;

    public ProductService(ProductRepository products, SiteRegistry sites, Clock clock) {
        this.products = products;
        this.sites = sites;
        this.clock = clock;
    }

    /** @throws IllegalArgumentException с текстом для пользователя, если ссылку добавить нельзя */
    @Transactional
    public ProductEntity add(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Ссылка должна начинаться с http:// или https://");
        }
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректная ссылка");
        }
        Site site = sites.forUrl(url).orElseThrow(() -> new IllegalArgumentException(
                "Для этого сайта нет профиля. Поддерживаются: "
                        + sites.all().stream().map(Site::host).collect(Collectors.joining(", "))));
        if (products.findByUrl(url).isPresent()) {
            throw new IllegalArgumentException("Этот товар уже отслеживается");
        }
        return products.save(new ProductEntity(url, site.code(), clock.instant()));
    }

    @Transactional
    public void delete(Long id) {
        products.deleteById(id);
    }
}
