package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.common.config.PriceClientProperties;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalPricesResponse;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenRequest;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 가상 시세 서버 클라이언트.
 *
 * <p>이 단계에서 일부러 하지 않는 것
 * <ul>
 *   <li><b>토큰 재사용</b>: 시세를 가져올 때마다 {@code POST /auth/token}으로 새 토큰을 받는다.
 *       시나리오 S10이 금지하는 방식이다. (3단계에서 {@code TokenCache}로 해결)</li>
 *   <li><b>401 재시도</b>: 토큰이 만료되어 401이 오면 그대로 실패한다.</li>
 *   <li><b>환율 조회</b>: {@code GET /v1/fx}를 부르지 않는다. 1단계는 달러 상품도 원화처럼 계산한다.
 *       (그래서 달러 상품의 평가금액이 환율 배수만큼 작게 나온다 — 2단계에서 해결)</li>
 *   <li><b>장애 대비</b>: 503이나 타임아웃이 나면 예외가 그대로 올라가 API가 500이 된다.
 *       요구사항 R6 위반이다. (3단계에서 스냅샷 보관으로 해결)</li>
 * </ul>
 */
@Component
public class PriceClient {

    private static final Logger log = LoggerFactory.getLogger(PriceClient.class);

    private final PriceClientProperties properties;
    private final RestClient restClient;

    public PriceClient(PriceClientProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getCallTimeoutMs());
        factory.setReadTimeout((int) properties.getCallTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public ExternalPricesResponse fetchPrices() {
        String token = issueToken(); // 호출할 때마다 새로 발급받는다 (S10 위반)
        return restClient.get()
                .uri("/v1/prices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExternalPricesResponse.class);
    }

    private String issueToken() {
        ExternalTokenResponse response = restClient.post()
                .uri("/auth/token")
                .body(new ExternalTokenRequest(properties.getClientId(), properties.getClientSecret()))
                .retrieve()
                .body(ExternalTokenResponse.class);
        // 몇 번이나 발급받는지 로그로 보이게 둔다. 3단계에서 이 줄이 거의 찍히지 않게 바뀐다.
        log.info("시세 서버 토큰을 새로 발급받았습니다.");
        return response.getAccessToken();
    }
}
