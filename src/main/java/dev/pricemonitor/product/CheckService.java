package dev.pricemonitor.product;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.scrape.PageFetcher;
import dev.pricemonitor.scrape.PageParser;
import dev.pricemonitor.scrape.PageParser.ParsedPage;
import dev.pricemonitor.scrape.ScrapeException;
import dev.pricemonitor.site.Site;
import dev.pricemonitor.site.SiteRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Проверка цен. HTTP-запрос идёт вне транзакции: держать соединение с БД, пока сайт отвечает
 * 10 секунд, незачем. Каждая проверка сохраняется сразу — сбой на 200-м товаре не теряет первые 199.
 */
@Service
public class CheckService {

    private static final Logger log = LoggerFactory.getLogger(CheckService.class);

    private final ProductRepository products;
    private final PriceCheckRepository checks;
    private final SiteRegistry sites;
    private final PageFetcher fetcher;
    private final MonitorProperties props;
    private final ExecutorService executor;
    private final Clock clock;

    /** Защита от двойного запуска: расписание и кнопка «Проверить все» в одно время. */
    private final AtomicBoolean running = new AtomicBoolean();

    public CheckService(ProductRepository products, PriceCheckRepository checks, SiteRegistry sites,
                        PageFetcher fetcher, MonitorProperties props, ExecutorService checkExecutor, Clock clock) {
        this.products = products;
        this.checks = checks;
        this.sites = sites;
        this.fetcher = fetcher;
        this.props = props;
        this.executor = checkExecutor;
        this.clock = clock;
    }

    /** @return false, если проверка уже идёт */
    public boolean startCheckAll() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        executor.submit(() -> {
            try {
                checkAll();
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    public void startCheck(Long productId) {
        executor.submit(() -> products.findById(productId).ifPresent(this::check));
    }

    public boolean isRunning() {
        return running.get();
    }

    void checkAll() {
        List<ProductEntity> all = products.findAllByOrderBySiteCodeAscIdAsc();
        log.info("Проверка цен: {} товаров", all.size());
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) {
                pause();
            }
            check(all.get(i));
        }
        log.info("Проверка цен завершена");
    }

    public PriceCheckEntity check(ProductEntity product) {
        PriceCheckEntity result;
        try {
            Site site = sites.byCode(product.getSiteCode())
                    .orElseThrow(() -> new ScrapeException("Профиль сайта " + product.getSiteCode() + " удалён из настроек"));
            Document page = fetcher.fetch(product.getUrl());
            ParsedPage parsed = PageParser.parse(page, site);
            if (parsed.name() != null && !parsed.name().equals(product.getName())) {
                product.rename(parsed.name());
                products.save(product);
            }
            result = PriceCheckEntity.ok(product.getId(), clock.instant(), parsed.price(), parsed.available());
        } catch (ScrapeException e) {
            result = PriceCheckEntity.error(product.getId(), clock.instant(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Неожиданная ошибка при проверке {}", product.getUrl(), e);
            result = PriceCheckEntity.error(product.getId(), clock.instant(), "Внутренняя ошибка: " + e.getMessage());
        }
        // Товар могли удалить, пока шёл запрос, — тогда FK не даст сохранить проверку.
        if (!products.existsById(product.getId())) {
            return result;
        }
        return checks.save(result);
    }

    private void pause() {
        try {
            Thread.sleep(props.delayBetweenRequests().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
