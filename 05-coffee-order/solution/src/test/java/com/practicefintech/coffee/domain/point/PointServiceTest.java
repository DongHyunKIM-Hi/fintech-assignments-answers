package com.practicefintech.coffee.domain.point;

import com.practicefintech.coffee.domain.point.service.PointService;
import com.practicefintech.coffee.support.ConcurrentRunner;
import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PointServiceTest extends IntegrationTestSupport {

    @Autowired
    PointService pointService;

    @Test
    @DisplayName("처음 충전하면 잔액이 생기고, 다시 충전하면 더해진다")
    void chargeAccumulates() {
        pointService.charge(1L, 3000);
        pointService.charge(1L, 2000);

        assertThat(pointService.getPoint(1L).getBalance()).isEqualTo(5000);
        assertThat(pointService.getPoint(2L).getBalance()).isZero();
    }

    @Test
    @DisplayName("[동시성] 처음 충전하는 사용자에게 100P 충전 100건이 동시에 와도 잔액은 10,000P")
    void concurrentCharge() throws Exception {
        List<ConcurrentRunner.Result<Object>> results = ConcurrentRunner.run(100, i -> pointService.charge(1L, 100));

        assertThat(results).allMatch(ConcurrentRunner.Result::success);
        assertThat(balanceOf(1L)).isEqualTo(10_000);
    }
}
