package dev.pricemonitor.scrape;

import dev.pricemonitor.config.MonitorProperties;
import java.io.IOException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class PageFetcher {

    private final MonitorProperties props;

    public PageFetcher(MonitorProperties props) {
        this.props = props;
    }

    /** Ошибки сети и HTTP превращаются в понятный текст — он попадёт в историю проверок. */
    public Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(props.userAgent())
                    .timeout((int) props.requestTimeout().toMillis())
                    .get();
        } catch (HttpStatusException e) {
            throw new ScrapeException("Сайт ответил " + e.getStatusCode()
                    + (e.getStatusCode() == 404 ? " — страница товара не найдена" : ""));
        } catch (IOException e) {
            throw new ScrapeException("Сайт недоступен: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " — " + e.getMessage() : ""));
        }
    }
}
