package dev.pricemonitor.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("monitor")
public record MonitorProperties(
        ZoneId zone,
        Duration requestTimeout,
        Duration delayBetweenRequests,
        String userAgent,
        Integer historyDays,
        boolean demoMode,
        String demoBaseUrl,
        String adminUsername,
        String adminPassword,
        List<SiteProps> sites) {

    public MonitorProperties {
        zone = zone != null ? zone : ZoneId.of("Europe/Minsk");
        requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(10);
        delayBetweenRequests = delayBetweenRequests != null ? delayBetweenRequests : Duration.ofSeconds(1);
        userAgent = userAgent != null ? userAgent : "PriceMonitorDemo/1.0";
        historyDays = historyDays != null ? historyDays : 30;
        demoBaseUrl = demoBaseUrl != null ? demoBaseUrl : "http://localhost:8080";
        sites = sites != null ? List.copyOf(sites) : List.of();
    }

    /**
     * Профиль сайта: по каким CSS-селекторам на странице товара лежат название, цена и наличие.
     * Новый сайт подключается добавлением профиля, без изменения кода.
     */
    public record SiteProps(
            String code,
            String host,
            String name,
            String nameSelector,
            String priceSelector,
            String availabilitySelector,
            String inStockPattern) {
    }
}
