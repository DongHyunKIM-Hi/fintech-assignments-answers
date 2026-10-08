package com.practicefintech.portfolio.price;

import com.practicefintech.portfolio.price.external.ExternalFxResponse;
import com.practicefintech.portfolio.price.external.ExternalPricesResponse;
import com.practicefintech.portfolio.price.external.ExternalTokenRequest;
import com.practicefintech.portfolio.price.external.ExternalTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 가상 시세 서버 클라이언트. 토큰을 캐시해 두었다가 만료 직전에만 새로 받고(S10),
 * 401을 받으면 토큰을 무효화하고 한 번 재발급받아 재시도한다.
 */
@Component
public class PriceClient {

    private static final Logger log = LoggerFactory.getLogger(PriceClient.class);

    private final PriceClientProperties properties;
    private final RestClient restClient;
    private final TokenCache tokenCache = new TokenCache();

    public PriceClient(PriceClientProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.callTimeoutMs());
        factory.setReadTimeout((int) properties.callTimeoutMs());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public ExternalPricesResponse fetchPrices() {
        return callWithAuth(token -> restClient.get()
                .uri("/v1/prices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExternalPricesResponse.class));
    }

    public ExternalFxResponse fetchFx() {
        return callWithAuth(token -> restClient.get()
                .uri("/v1/fx?pair=USD-KRW")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(ExternalFxResponse.class));
    }

    private <T> T callWithAuth(java.util.function.Function<String, T> call) {
        String token = ensureToken();
        try {
            return call.apply(token);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.info("시세 서버 토큰이 만료된 것으로 보여 재발급 후 재시도합니다.");
                tokenCache.invalidate();
                String freshToken = ensureToken();
                return call.apply(freshToken);
            }
            throw e;
        }
    }

    private synchronized String ensureToken() {
        if (!tokenCache.isValid(properties.tokenRefreshMarginSeconds())) {
            ExternalTokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .body(new ExternalTokenRequest(properties.clientId(), properties.clientSecret()))
                    .retrieve()
                    .body(ExternalTokenResponse.class);
            tokenCache.set(response.accessToken(), response.expiresIn());
        }
        return tokenCache.get();
    }
}
