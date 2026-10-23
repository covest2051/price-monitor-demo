package dev.pricemonitor.product;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.site.Site;
import dev.pricemonitor.site.SiteRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final ProductRepository products;
    private final PriceCheckRepository checks;
    private final SiteRegistry sites;
    private final MonitorProperties props;
    private final Clock clock;

    public DashboardService(ProductRepository products, PriceCheckRepository checks, SiteRegistry sites,
                            MonitorProperties props, Clock clock) {
        this.products = products;
        this.checks = checks;
        this.sites = sites;
        this.props = props;
        this.clock = clock;
    }

    /** Три запроса на любое число товаров — без N+1 по истории каждого. */
    public List<ProductRow> rows() {
        Map<Long, PriceCheckEntity> latest = checks.findLatestPerProduct().stream()
                .collect(Collectors.toMap(PriceCheckEntity::getProductId, Function.identity()));
        Map<Long, List<PriceCheckEntity>> lastTwoOk = checks.findLastTwoOkPerProduct().stream()
                .collect(Collectors.groupingBy(PriceCheckEntity::getProductId));
        List<ProductRow> rows = new ArrayList<>();
        for (ProductEntity p : products.findAllByOrderByIdAsc()) {
            List<PriceCheckEntity> ok = lastTwoOk.getOrDefault(p.getId(), List.of());
            PriceCheckEntity current = ok.isEmpty() ? null : ok.get(0);
            PriceCheckEntity previous = ok.size() > 1 ? ok.get(1) : null;
            rows.add(new ProductRow(p, siteName(p.getSiteCode()), latest.get(p.getId()),
                    current != null ? current.getPrice() : null,
                    previous != null ? previous.getPrice() : null,
                    current != null ? current.getAvailable() : null));
        }
        return rows;
    }

    public ProductRow row(Long productId) {
        return rows().stream().filter(r -> r.product().getId().equals(productId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));
    }

    public List<PriceCheckEntity> history(Long productId) {
        return checks.findByProductIdAndCheckedAtAfterOrderByCheckedAtDesc(productId, historyFrom());
    }

    public List<PriceCheckEntity> historyOfAll() {
        return checks.findByCheckedAtAfterOrderByProductIdAscCheckedAtDesc(historyFrom());
    }

    public String siteName(String code) {
        return sites.byCode(code).map(Site::name).orElse(code);
    }

    private Instant historyFrom() {
        return clock.instant().minus(Duration.ofDays(props.historyDays()));
    }
}
