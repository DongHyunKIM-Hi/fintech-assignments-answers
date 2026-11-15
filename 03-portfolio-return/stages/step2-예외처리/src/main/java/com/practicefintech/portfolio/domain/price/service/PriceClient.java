package com.practicefintech.portfolio.domain.price.service;

import com.practicefintech.portfolio.common.config.PriceClientProperties;
import com.practicefintech.portfolio.domain.price.model.dto.ExternalFxResponse;
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
 * 가상 시세 서버 클라이언트. 2단계는 <b>요청이 들어올 때마다</b> 시세와 환율을 받아 온다.
 *
 * <p>이 단계에서 일부러 하지 않는 것 (모두 3단계 몫)
 * <ul>
 *   <li><b>토큰 재사용</b>: 호출할 때마다 {@code POST /auth/token}으로 새 토큰을 받는다.
 *       토큰이 만료될 일이 없으니 "갱신이 계속되는지"는 통과하지만, 시나리오 S10이 함께 요구하는
 *       "시세를 가져올 때마다 토큰을 새로 발급받지 않는다"를 어긴다. 아래 로그가 몇 번 찍히는지 보면 된다.</li>
 *   <li><b>401 재시도</b>: 토큰 문제로 401이 오면 그대로 실패한다.</li>
 *   <li><b>장애 대비</b>: 503이나 타임아웃이 나면 예외가 그대로 올라가 GET API가 500이 된다.
 *       요구사항 R6 위반이고, 평가 기준표의 큰 감점 항목이다.</li>
 *   <li><b>30초 주기 갱신</b>: 외부 호출이 요청 경로 안에 있어서 주기 갱신이라는 개념이 아직 없다.
 *       조회할 때마다 시세 서버를 부르므로 조회가 느려지고, 시세 서버의 부하도 그만큼 커진다.</li>
 * </ul>
 * 3단계에서 {@code PriceSnapshotStore}(마지막 성공 값 보관), {@code PriceRefreshScheduler}(30초 주기),
 * {@code TokenCache}(토큰 재사용) 세 클래스를 넣어 외부 호출을 요청 경로 밖으로 빼낸다.
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
        return restClient.get()
                .uri("/v1/prices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken())
                .retrieve()
                .body(ExternalPricesResponse.class);
    }

    public ExternalFxResponse fetchFx() {
        return restClient.get()
                .uri("/v1/fx?pair=USD-KRW")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + issueToken())
                .retrieve()
                .body(ExternalFxResponse.class);
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
