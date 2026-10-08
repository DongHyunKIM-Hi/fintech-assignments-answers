package com.practicefintech.refundrelay.worker;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * 가상 결제대행사(pg-mock)의 POST /pg/refunds를 호출한다.
 * 응답 대기 시간을 넘기면 PG_TIMEOUT으로 본다 — 이게 T1 함정(시간 초과인데 실제로는
 * 처리된 경우)의 전제 조건이다. 재시도할 때도 이 클래스는 항상 같은 refundId를 그대로
 * 보내므로, pg-mock이 "이미 처리됨"으로 답해 주면 자연히 이중 환불이 막힌다.
 */
@Component
public class PgClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final Duration timeout;

    public PgClient(PgClientProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.baseUrl();
        this.timeout = Duration.ofMillis(properties.callTimeoutMs());
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.objectMapper = objectMapper;
    }

    public PgCallResult send(String refundId, String orderId, long amount) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("refundId", refundId, "orderId", orderId, "amount", amount));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/pg/refunds"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return PgCallResult.ERROR;
            }
            JsonNode json = objectMapper.readTree(response.body());
            return "SUCCESS".equals(json.path("result").asText()) ? PgCallResult.SUCCESS : PgCallResult.DECLINED;
        } catch (HttpTimeoutException e) {
            return PgCallResult.TIMEOUT;
        } catch (Exception e) {
            return PgCallResult.ERROR;
        }
    }
}
