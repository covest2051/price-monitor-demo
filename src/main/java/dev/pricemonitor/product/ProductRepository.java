package dev.pricemonitor.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByUrl(String url);

    /** Для проверки: товары одного сайта подряд. */
    List<ProductEntity> findAllByOrderBySiteCodeAscIdAsc();

    /** Для кабинета: в порядке добавления. */
    List<ProductEntity> findAllByOrderByIdAsc();
}
