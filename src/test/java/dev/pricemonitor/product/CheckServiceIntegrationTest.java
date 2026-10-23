package dev.pricemonitor.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "monitor.demo-mode=false",
        "monitor.schedule=-",
        "monitor.admin-password=test",
        "monitor.delay-between-requests=0s",
        "monitor.sites[0].code=stub",
        "monitor.sites[0].host=127.0.0.1",
        "monitor.sites[0].name=Тестовый магазин",
        "monitor.sites[0].name-selector=h1",
        "monitor.sites[0].price-selector=.price",
        "monitor.sites[0].availability-selector=.stock",
        "monitor.sites[0].in-stock-pattern=^В наличии"
})
@Testcontainers
class CheckServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Страница «магазина»; null — сайт отвечает 404. */
    private static final AtomicReference<String> page = new AtomicReference<>();
    private static HttpServer shop;

    @Autowired
    private ProductService productService;
    @Autowired
    private CheckService checkService;
    @Autowired
    private DashboardService dashboard;
    @Autowired
    private ProductRepository products;

    @BeforeAll
    static void startShop() throws IOException {
        shop = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        shop.createContext("/item", exchange -> {
            String html = page.get();
            byte[] body = (html != null ? html : "not found").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(html != null ? 200 : 404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        shop.start();
    }

    @AfterAll
    static void stopShop() {
        shop.stop(0);
    }

    @BeforeEach
    void clean() {
        products.deleteAll();
    }

    @Test
    void historyTracksPriceChangesStockAndErrorsSeparately() {
        ProductEntity product = productService.add("http://127.0.0.1:" + shop.getAddress().getPort() + "/item");

        page.set(html("1 299,90 руб.", "В наличии"));
        checkService.check(product);
        page.set(html("1 249,90 руб.", "В наличии"));
        checkService.check(product);

        ProductRow row = dashboard.row(product.getId());
        assertThat(row.product().getName()).isEqualTo("Кофемашина");
        assertThat(row.price()).isEqualByComparingTo("1249.90");
        assertThat(row.change()).isEqualByComparingTo("-50.00");
        assertThat(row.changePercent()).isEqualByComparingTo("-3.8");

        page.set(html("1 249,90 руб.", "Нет в наличии"));
        checkService.check(product);
        assertThat(dashboard.row(product.getId()).available()).isFalse();

        // Сайт упал: последняя известная цена остаётся, ошибка видна отдельно от «нет в наличии».
        page.set(null);
        checkService.check(product);
        row = dashboard.row(product.getId());
        assertThat(row.price()).isEqualByComparingTo(new BigDecimal("1249.90"));
        assertThat(row.lastError()).contains("404");
        assertThat(dashboard.history(product.getId())).hasSize(4);
    }

    @Test
    void unknownSiteIsRejected() {
        assertThatThrownBy(() -> productService.add("https://unknown-shop.example/item/1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("нет профиля");
    }

    private static String html(String price, String stock) {
        return "<h1>Кофемашина</h1><div class=\"price\">" + price + "</div><div class=\"stock\">" + stock + "</div>";
    }
}
