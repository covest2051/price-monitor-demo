package dev.pricemonitor.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_check")
public class PriceCheckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CheckStatus status;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "available")
    private Boolean available;

    @Column(name = "error")
    private String error;

    protected PriceCheckEntity() {
    }

    public static PriceCheckEntity ok(Long productId, Instant at, BigDecimal price, boolean available) {
        PriceCheckEntity check = new PriceCheckEntity();
        check.productId = productId;
        check.checkedAt = at;
        check.status = CheckStatus.OK;
        check.price = price;
        check.available = available;
        return check;
    }

    public static PriceCheckEntity error(Long productId, Instant at, String error) {
        PriceCheckEntity check = new PriceCheckEntity();
        check.productId = productId;
        check.checkedAt = at;
        check.status = CheckStatus.ERROR;
        check.error = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        return check;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Boolean getAvailable() {
        return available;
    }

    public String getError() {
        return error;
    }

    public boolean isOk() {
        return status == CheckStatus.OK;
    }
}
