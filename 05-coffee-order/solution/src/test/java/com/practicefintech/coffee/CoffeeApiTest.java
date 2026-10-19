package com.practicefintech.coffee;

import com.practicefintech.coffee.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 4개의 요청과 응답 형식, 에러 응답을 HTTP 수준에서 확인한다. */
@AutoConfigureMockMvc
class CoffeeApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("메뉴 목록 조회: 메뉴 ID, 이름, 가격을 응답한다")
    void getMenus() throws Exception {
        mockMvc.perform(get("/api/menus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].menuId").value(1))
                .andExpect(jsonPath("$[0].name").value("아메리카노"))
                .andExpect(jsonPath("$[0].price").value(4500));
    }

    @Test
    @DisplayName("포인트 충전: 충전 후 잔액을 응답한다 (1원 = 1P)")
    void charge() throws Exception {
        charge(1, 10000).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.balance").value(10000));
    }

    @Test
    @DisplayName("포인트 충전: 0원 이하나 1회 한도(100만 원)를 넘는 금액은 400")
    void chargeInvalidAmount() throws Exception {
        charge(1, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        charge(1, -100).andExpect(status().isBadRequest());
        charge(1, 1_000_001).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("포인트 충전: 10.5처럼 소수인 금액은 잘라서 충전하지 않고 400")
    void chargeDecimalAmount() throws Exception {
        mockMvc.perform(post("/api/points/charge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"amount\":10.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(balanceOf(1L)).isZero();
    }

    @Test
    @DisplayName("잘못된 경로 값, 없는 경로, 지원하지 않는 메서드도 같은 에러 형식으로 응답한다")
    void errorFormat() throws Exception {
        mockMvc.perform(get("/api/points/abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(get("/api/nothing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").exists());
        mockMvc.perform(delete("/api/menus"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").exists());
    }

    @Test
    @DisplayName("주문: 성공하면 201과 결제 금액, 남은 포인트를 응답한다")
    void order() throws Exception {
        charge(1, 10000);
        order(1, 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.menuId").value(1))
                .andExpect(jsonPath("$.paidAmount").value(4500))
                .andExpect(jsonPath("$.remainingPoint").value(5500));
    }

    @Test
    @DisplayName("주문: 없는 메뉴는 404, 포인트 부족은 409, 필수값 누락은 400")
    void orderErrors() throws Exception {
        charge(1, 4000);
        order(1, 999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MENU_NOT_FOUND"));
        order(1, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));
        order(2, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_POINT"));
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인기 메뉴 조회: 주문이 없으면 빈 배열, 주문이 있으면 순위와 주문 횟수를 응답한다")
    void popularMenus() throws Exception {
        mockMvc.perform(get("/api/menus/popular")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        charge(1, 100000);
        order(1, 2);
        order(1, 2);
        order(1, 1);

        mockMvc.perform(get("/api/menus/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].menuId").value(2))
                .andExpect(jsonPath("$[0].orderCount").value(2))
                .andExpect(jsonPath("$[1].menuId").value(1));
    }

    private ResultActions charge(long userId, long amount) throws Exception {
        return mockMvc.perform(post("/api/points/charge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"amount\":" + amount + "}"));
    }

    private ResultActions order(long userId, long menuId) throws Exception {
        return mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}"));
    }
}
