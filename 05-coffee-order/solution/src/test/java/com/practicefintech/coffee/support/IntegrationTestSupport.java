package com.practicefintech.coffee.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL과 Redis(Testcontainers)를 띄워 테스트한다.
 * 분산 락과 Redis 집계는 H2나 가짜 객체로는 확인할 수 없기 때문이다.
 * 컨테이너는 모든 테스트 클래스가 함께 쓰도록 한 번만 띄운다. (Docker 실행 필요)
 */
@SpringBootTest(properties = "coffee.outbox-retry-millis=3600000")
public abstract class IntegrationTestSupport {

    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("coffee");
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_event");
        jdbcTemplate.update("DELETE FROM coffee_order");
        jdbcTemplate.update("DELETE FROM user_point");
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    protected long balanceOf(long userId) {
        return jdbcTemplate.query("SELECT balance FROM user_point WHERE user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : 0L, userId);
    }

    protected long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
