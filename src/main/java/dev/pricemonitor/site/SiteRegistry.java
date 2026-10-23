package dev.pricemonitor.site;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.config.MonitorProperties.SiteProps;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Профили сайтов из конфига, проверенные при старте. */
@Component
public class SiteRegistry {

    private final Map<String, Site> byCode = new LinkedHashMap<>();

    public SiteRegistry(MonitorProperties props) {
        for (SiteProps s : props.sites()) {
            require(s.code() != null && s.host() != null && s.priceSelector() != null,
                    "У профиля сайта обязательны code, host и price-selector: " + s);
            Pattern inStock = s.inStockPattern() == null ? null
                    : Pattern.compile(s.inStockPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Site site = new Site(s.code(), s.host().toLowerCase(Locale.ROOT), s.name() != null ? s.name() : s.host(),
                    s.nameSelector(), s.priceSelector(), s.availabilitySelector(), inStock);
            require(byCode.putIfAbsent(site.code(), site) == null, "Код сайта повторяется: " + site.code());
        }
    }

    public List<Site> all() {
        return List.copyOf(byCode.values());
    }

    public Optional<Site> byCode(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    /** Сайт определяется по хосту ссылки; www. и порт не учитываются. */
    public Optional<Site> forUrl(String url) {
        String host = hostOf(url);
        return byCode.values().stream().filter(s -> s.host().equals(host)).findFirst();
    }

    static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
