package dev.pricemonitor.demo;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.product.CheckService;
import dev.pricemonitor.product.PriceCheckEntity;
import dev.pricemonitor.product.PriceCheckRepository;
import dev.pricemonitor.product.ProductEntity;
import dev.pricemonitor.product.ProductRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Демо-режим: при первом старте добавляет товары демо-магазина с историей за прошлые дни
 * и несколько страниц books.toscrape.com (песочница для парсинга), затем запускает проверку.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<String> SANDBOX_PAGES = List.of(
            "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html",
            "https://books.toscrape.com/catalogue/tipping-the-velvet_999/index.html",
            "https://books.toscrape.com/catalogue/soumission_998/index.html");

    private final ProductRepository products;
    private final PriceCheckRepository checks;
    private final CheckService checkService;
    private final MonitorProperties props;
    private final Clock clock;

    public DemoDataSeeder(ProductRepository products, PriceCheckRepository checks, CheckService checkService,
                          MonitorProperties props, Clock clock) {
        this.products = products;
        this.checks = checks;
        this.checkService = checkService;
        this.props = props;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!props.demoMode() || products.count() > 0) {
            return;
        }
        seedData();
        log.info("Демо-данные добавлены, запускаю первую проверку");
        checkService.startCheckAll();
    }

    private void seedData() {
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        for (DemoShop.Item item : DemoShop.ITEMS) {
            ProductEntity product = new ProductEntity(
                    props.demoBaseUrl() + "/demo-shop/products/" + item.id(), "demo", clock.instant());
            product.rename(item.name());
            products.save(product);
            for (int daysAgo = props.historyDays(); daysAgo >= 1; daysAgo--) {
                LocalDate date = today.minusDays(daysAgo);
                DemoShop.Offer offer = DemoShop.offer(item, date);
                checks.save(PriceCheckEntity.ok(product.getId(), date.atTime(9, 0).atZone(props.zone()).toInstant(),
                        offer.price(), offer.available()));
            }
        }
        for (String url : SANDBOX_PAGES) {
            products.save(new ProductEntity(url, "books", clock.instant()));
        }
    }
}
