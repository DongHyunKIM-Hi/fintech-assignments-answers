CREATE TABLE IF NOT EXISTS menu (
    id    BIGINT       NOT NULL PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL,
    price BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS user_point (
    user_id    BIGINT   NOT NULL PRIMARY KEY,
    balance    BIGINT   NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    -- 락이 잘못 걸려도 잔액이 음수가 되지 않게 막는 마지막 방어선
    CONSTRAINT chk_user_point_balance CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS coffee_order (
    id         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    menu_id    BIGINT   NOT NULL,
    price      BIGINT   NOT NULL,
    ordered_at DATETIME(6) NOT NULL,
    INDEX idx_coffee_order_ordered_at (ordered_at)
);

-- 데이터 수집 플랫폼에 보낼 주문 이벤트 (아웃박스)
-- 주문과 같은 트랜잭션에서 저장하므로, 주문이 저장되면 보낼 이벤트도 반드시 남는다.
CREATE TABLE IF NOT EXISTS order_event (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT      NOT NULL UNIQUE,
    user_id       BIGINT      NOT NULL,
    menu_id       BIGINT      NOT NULL,
    amount        BIGINT      NOT NULL,
    status        VARCHAR(10) NOT NULL,
    attempt_count INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    sent_at       DATETIME(6) NULL,
    INDEX idx_order_event_status_created (status, created_at)
);
