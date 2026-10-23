package dev.pricemonitor.product;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceCheckRepository extends JpaRepository<PriceCheckEntity, Long> {

    /** Последняя проверка каждого товара (любой статус) — одним запросом через DISTINCT ON. */
    @Query(value = """
            select distinct on (product_id) *
            from price_check
            order by product_id, checked_at desc, id desc
            """, nativeQuery = true)
    List<PriceCheckEntity> findLatestPerProduct();

    /** Две последние удачные проверки каждого товара: текущая цена и предыдущая для расчёта изменения. */
    @Query(value = """
            select t.id, t.product_id, t.checked_at, t.status, t.price, t.available, t.error
            from (select pc.*,
                         row_number() over (partition by pc.product_id order by pc.checked_at desc, pc.id desc) as rn
                  from price_check pc
                  where pc.status = 'OK') t
            where t.rn <= 2
            order by t.product_id, t.checked_at desc, t.id desc
            """, nativeQuery = true)
    List<PriceCheckEntity> findLastTwoOkPerProduct();

    List<PriceCheckEntity> findByProductIdAndCheckedAtAfterOrderByCheckedAtDesc(Long productId, Instant from);

    List<PriceCheckEntity> findByCheckedAtAfterOrderByProductIdAscCheckedAtDesc(Instant from);

    boolean existsByProductId(Long productId);
}
