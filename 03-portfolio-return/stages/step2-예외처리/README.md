# 2단계 — 예외 처리와 시나리오

과제 3(가상 포트폴리오 수익률 서비스) 정답 코드를 세 단계로 나눈 것 중 두 번째입니다.
전체 설명은 `../README.md`를 보세요.

## 실행

```bash
docker run -p 9091:9091 ghcr.io/donghyunkim-hi/portfolio-price-server:1.1.0   # 가상 시세 서버
./gradlew bootRun                                                            # 포트 8080
```

## 1단계에서 더하는 것

- **CSV 정리**: 중복 코드는 먼저 나온 행만, 통화는 `KRW`/`USD`, 상장일은 `LocalDate`, 빈 위험등급은 `null`
- **상품 조회**: 검색, 유형·통화·위험등급 필터(AND), 정렬, 페이지
- **포트폴리오 목록**: 이름 검색, 정렬, 페이지
- **입력 검증과 에러 응답**: `GlobalExceptionHandler`로 `{"code","message"}` 통일, 400 `INVALID_REQUEST`,
  400 `UNKNOWN_PRODUCT`, 404 `PORTFOLIO_NOT_FOUND`, 503 `PRICE_NOT_READY`
- **계산 규칙**: 매입가와 매입 시점 환율 고정, 금액 원 단위 버림, 수익률 HALF_UP 반올림, 오늘 손익

## 여전히 하지 않는 것 (3단계 몫)

| 하지 않는 것 | 그래서 생기는 일 |
|---|---|
| 30초 주기 시세 갱신 | 조회할 때마다 시세 서버를 부릅니다 (요청 경로 안에 외부 호출이 있습니다) |
| 토큰 재사용 | 시세를 가져올 때마다 토큰을 새로 발급받습니다 (시나리오 S10 위반) |
| 마지막 시세 보관 | 시세 서버가 멈추면 GET API가 500입니다 (요구사항 R6, 시나리오 S11 위반 — 평가 기준표의 큰 감점 항목) |

3단계에서 `PriceSnapshotStore`, `PriceRefreshScheduler`, `TokenCache` 세 클래스를 넣어
외부 호출을 요청 경로 밖으로 빼내면 위 세 가지가 한 번에 해결됩니다.
