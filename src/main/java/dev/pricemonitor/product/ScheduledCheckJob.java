package dev.pricemonitor.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledCheckJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCheckJob.class);

    private final CheckService checkService;

    public ScheduledCheckJob(CheckService checkService) {
        this.checkService = checkService;
    }

    @Scheduled(cron = "${monitor.schedule}", zone = "${monitor.zone}")
    public void run() {
        if (!checkService.startCheckAll()) {
            log.info("Плановая проверка пропущена: предыдущая ещё идёт");
        }
    }
}
