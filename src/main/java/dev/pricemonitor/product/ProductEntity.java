package dev.pricemonitor.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, unique = true)
    private String url;

    @Column(name = "site_code", nullable = false)
    private String siteCode;

    /** Берётся со страницы при первой удачной проверке. */
    @Column(name = "name")
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductEntity() {
    }

    public ProductEntity(String url, String siteCode, Instant now) {
        this.url = url;
        this.siteCode = siteCode;
        this.createdAt = now;
    }

    public void rename(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName.length() > 500 ? newName.substring(0, 500) : newName;
        }
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return name != null ? name : url;
    }
}
