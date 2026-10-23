--liquibase formatted sql

--changeset pricemonitor:001-init
CREATE TABLE product
(
    id         BIGSERIAL PRIMARY KEY,
    url        VARCHAR(2000) NOT NULL UNIQUE,
    site_code  VARCHAR(50)   NOT NULL,
    name       VARCHAR(500),
    created_at TIMESTAMPTZ   NOT NULL
);

-- Историю храним целиком: OK со снятой ценой или ERROR с причиной. Удаление товара удаляет и его историю.
CREATE TABLE price_check
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL,
    status     VARCHAR(10) NOT NULL,
    price      NUMERIC(14, 2),
    available  BOOLEAN,
    error      VARCHAR(1000),
    CONSTRAINT price_check_ok_has_price CHECK (status <> 'OK' OR price IS NOT NULL)
);

-- Под выборки «последняя проверка товара» и «история за период»
CREATE INDEX price_check_product_checked_idx ON price_check (product_id, checked_at DESC);
CREATE INDEX price_check_checked_idx ON price_check (checked_at);
