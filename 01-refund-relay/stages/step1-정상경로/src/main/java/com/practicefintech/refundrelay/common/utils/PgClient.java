package com.practicefintech.refundrelay.common.utils;

import com.practicefintech.refundrelay.common.config.PgClientProperties;
import com.practicefintech.refundrelay.common.enums.PgCallResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * 가상 결제대행사(pg-mock)의 POST /pg/refunds를 호출한다.
 *
 * 1단계에서 일부러 하지 않는 것
 * - 응답 대기 시간(타임아웃)을 두지 않는다. 결제대행사가 10초를 끌면 10초를 그대로 기다린다.
 *   기다리는 동안 워커 스레드가 묶이므로 뒤에 접수된 다른 건들도 함께 밀린다.
 *
 * 왜 다음 단계가 필요한가
 * - 2단계에서 5초 응답 대기 시간을 걸고, 초과하면 PG_TIMEOUT으로 보고 같은 refundId로 재시도한다.
 */
@Component
public class PgClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PgClient(PgClientProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.getBaseUrl();
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    public PgCallResult send(String refundId, String orderId, long amount) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("refundId", refundId, "orderId", orderId, "amount", amount));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/pg/refunds"))
                    // .timeout(...)을 걸지 않았다. 2단계에서 추가한다.
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return PgCallResult.ERROR;
            }
            JsonNode json = objectMapper.readTree(response.body());
            return "SUCCESS".equals(json.path("result").asText()) ? PgCallResult.SUCCESS : PgCallResult.DECLINED;
        } catch (Exception e) {
            return PgCallResult.ERROR;
        }
    }
}
