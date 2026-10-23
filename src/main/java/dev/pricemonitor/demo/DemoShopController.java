package dev.pricemonitor.demo;

import dev.pricemonitor.config.MonitorProperties;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DemoShopController {

    private final Clock clock;
    private final MonitorProperties props;

    public DemoShopController(Clock clock, MonitorProperties props) {
        this.clock = clock;
        this.props = props;
    }

    @GetMapping("/demo-shop")
    public String index(Model model) {
        LocalDate today = LocalDate.now(clock.withZone(props.zone()));
        model.addAttribute("offers", DemoShop.ITEMS.stream().map(i -> DemoShop.offer(i, today)).toList());
        model.addAttribute("fmt", this);
        return "demo/index";
    }

    @GetMapping("/demo-shop/products/{id}")
    public String product(@PathVariable int id, Model model) {
        DemoShop.Item item = DemoShop.item(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("offer", DemoShop.offer(item, LocalDate.now(clock.withZone(props.zone()))));
        model.addAttribute("fmt", this);
        return "demo/product";
    }

    /** Цена в «магазинном» виде: "1 254,90 руб." — парсер должен справляться и с таким форматом. */
    public String price(BigDecimal price) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.of("ru"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(price) + " руб.";
    }
}
