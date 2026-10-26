package dev.pricemonitor.web;

import dev.pricemonitor.config.MonitorProperties;
import dev.pricemonitor.pricelist.DemoPriceLists;
import dev.pricemonitor.pricelist.PriceListExcelExporter;
import dev.pricemonitor.pricelist.PriceListReport;
import dev.pricemonitor.pricelist.PriceListService;
import dev.pricemonitor.pricelist.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/** Прайсы поставщиков: пример на демо-прайсах и обработка своих файлов с выгрузкой в Excel. */
@Controller
@RequestMapping("/pricelists")
public class PriceListController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PriceListService service;
    private final PriceListExcelExporter exporter;
    private final DemoPriceLists demo;
    private final MonitorProperties props;
    private final Clock clock;

    public PriceListController(PriceListService service, PriceListExcelExporter exporter, DemoPriceLists demo,
                               MonitorProperties props, Clock clock) {
        this.service = service;
        this.exporter = exporter;
        this.demo = demo;
        this.props = props;
        this.clock = clock;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("report", demoReport());
        model.addAttribute("previousFiles", demo.files(DemoPriceLists.PREVIOUS));
        model.addAttribute("currentFiles", demo.files(DemoPriceLists.CURRENT));
        model.addAttribute("view", new ViewFormat(props.zone()));
        return "pricelists";
    }

    @GetMapping("/demo.xlsx")
    public ResponseEntity<byte[]> demoExcel() {
        return excel(exporter.export(demoReport()), "pricelists-demo.xlsx");
    }

    @GetMapping("/samples/{week}/{name}")
    public ResponseEntity<byte[]> sample(@PathVariable String week, @PathVariable String name) {
        return demo.file(week, name)
                .map(file -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.name()))
                        .contentType(MediaTypeFactory.getMediaType(file.name()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .body(file.content()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<byte[]> process(@RequestParam("current") List<MultipartFile> current,
                                          @RequestParam(value = "previous", required = false) List<MultipartFile> previous) {
        List<SourceFile> currentFiles = files(current);
        if (currentFiles.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .body("Выберите хотя бы один прайс".getBytes(StandardCharsets.UTF_8));
        }
        PriceListReport report = service.process(files(previous), currentFiles);
        String fileName = "pricelists-" + LocalDate.now(clock.withZone(props.zone())).format(DateTimeFormatter.ISO_DATE) + ".xlsx";
        return excel(exporter.export(report), fileName);
    }

    private PriceListReport demoReport() {
        return service.process(demo.files(DemoPriceLists.PREVIOUS), demo.files(DemoPriceLists.CURRENT));
    }

    private static List<SourceFile> files(List<MultipartFile> uploads) {
        if (uploads == null) {
            return List.of();
        }
        return uploads.stream()
                .filter(upload -> !upload.isEmpty() && upload.getOriginalFilename() != null)
                .map(upload -> {
                    try {
                        return new SourceFile(upload.getOriginalFilename(), upload.getBytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toList();
    }

    private static ResponseEntity<byte[]> excel(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(fileName))
                .contentType(XLSX)
                .body(body);
    }

    private static String attachment(String fileName) {
        return ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString();
    }
}
