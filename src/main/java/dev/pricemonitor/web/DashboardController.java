package dev.pricemonitor.web;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.export.ExcelExporter;
import dev.pricemonitor.product.CheckService;
import dev.pricemonitor.product.DashboardService;
import dev.pricemonitor.product.PriceCheckEntity;
import dev.pricemonitor.product.ProductEntity;
import dev.pricemonitor.product.ProductRow;
import dev.pricemonitor.product.ProductService;
import dev.pricemonitor.site.SiteRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DashboardController {

    private final DashboardService dashboard;
    private final ProductService productService;
    private final CheckService checkService;
    private final ExcelExporter exporter;
    private final SiteRegistry sites;
    private final MonitorProperties props;
    private final Clock clock;

    public DashboardController(DashboardService dashboard, ProductService productService, CheckService checkService,
                               ExcelExporter exporter, SiteRegistry sites, MonitorProperties props, Clock clock) {
        this.dashboard = dashboard;
        this.productService = productService;
        this.checkService = checkService;
        this.exporter = exporter;
        this.sites = sites;
        this.props = props;
        this.clock = clock;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<ProductRow> rows = dashboard.rows();
        model.addAttribute("rows", rows);
        model.addAttribute("sites", sites.all());
        model.addAttribute("running", checkService.isRunning());
        model.addAttribute("changedCount", rows.stream().filter(r -> r.change() != null && r.change().signum() != 0).count());
        model.addAttribute("outOfStockCount", rows.stream().filter(r -> Boolean.FALSE.equals(r.available())).count());
        model.addAttribute("errorCount", rows.stream().filter(r -> r.lastError() != null).count());
        model.addAttribute("view", new ViewFormat(props.zone()));
        return "index";
    }

    @GetMapping("/products/{id}")
    public String product(@PathVariable Long id, Model model) {
        ProductRow row = dashboard.row(id);
        List<PriceCheckEntity> history = dashboard.history(id);
        List<PriceCheckEntity> chart = history.stream()
                .filter(PriceCheckEntity::isOk)
                .sorted(Comparator.comparing(PriceCheckEntity::getCheckedAt))
                .toList();
        ViewFormat view = new ViewFormat(props.zone());
        model.addAttribute("row", row);
        model.addAttribute("history", history);
        model.addAttribute("chartLabels", chart.stream().map(c -> view.date(c.getCheckedAt())).toList());
        model.addAttribute("chartPrices", chart.stream().map(PriceCheckEntity::getPrice).toList());
        model.addAttribute("historyDays", props.historyDays());
        model.addAttribute("view", view);
        return "product";
    }

    @PostMapping("/products")
    public String add(@RequestParam String url, RedirectAttributes redirect) {
        try {
            ProductEntity product = productService.add(url);
            checkService.startCheck(product.getId());
            redirect.addFlashAttribute("message", "Товар добавлен, первая проверка уже идёт — обновите страницу через пару секунд.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            redirect.addFlashAttribute("url", url);
        }
        return "redirect:/";
    }

    @PostMapping("/products/{id}/check")
    public String check(@PathVariable Long id, RedirectAttributes redirect) {
        checkService.startCheck(id);
        redirect.addFlashAttribute("message", "Проверка запущена — обновите страницу через пару секунд.");
        return "redirect:/products/" + id;
    }

    @PostMapping("/products/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        productService.delete(id);
        redirect.addFlashAttribute("message", "Товар удалён из мониторинга.");
        return "redirect:/";
    }

    @PostMapping("/check-all")
    public String checkAll(RedirectAttributes redirect) {
        redirect.addFlashAttribute("message", checkService.startCheckAll()
                ? "Проверка всех товаров запущена — обновите страницу через минуту."
                : "Проверка уже идёт.");
        return "redirect:/";
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> export() {
        String fileName = "prices-" + LocalDate.now(clock.withZone(props.zone())).format(DateTimeFormatter.ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exporter.export());
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
