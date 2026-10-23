package dev.pricemonitor.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Один поток: проверки идут последовательно, чтобы не долбить сайты параллельными запросами. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService checkExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "price-check");
            thread.setDaemon(true);
            return thread;
        });
    }
}
