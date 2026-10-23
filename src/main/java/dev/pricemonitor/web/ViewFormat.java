package dev.pricemonitor.web;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Форматирование для шаблонов: деньги, проценты, даты в часовом поясе заказчика. */
public class ViewFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM");

    private final ZoneId zone;

    public ViewFormat(ZoneId zone) {
        this.zone = zone;
    }

    public String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("ru"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }

    public String signedMoney(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return (value.signum() > 0 ? "+" : "") + money(value);
    }

    public String signedPercent(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return (value.signum() > 0 ? "+" : "") + value.toPlainString().replace('.', ',') + "%";
    }

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(zone));
    }

    public String date(Instant instant) {
        return DATE.format(instant.atZone(zone));
    }
}
