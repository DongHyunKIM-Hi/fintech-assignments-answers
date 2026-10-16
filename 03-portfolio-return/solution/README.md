# 과제 3. 가상 포트폴리오 수익률 서비스 정답 코드

- 수강생에게 제공한 빈 시작 프로젝트(`starter-project/`)에 도메인 코드를 모두 새로 작성한 참고 구현입니다.
- 정답은 하나가 아닙니다. 특히 명세에 없는 빈틈(G1~G5)은 다른 결정이라도 근거가 있으면 인정합니다. (평가 기준표 B 영역)

## 실행 방법

```bash
# 1) 가상 시세 서버 실행 (포트 9091)
docker run -p 9091:9091 ghcr.io/donghyunkim-hi/portfolio-price-server:1.0.0

# 2) 정답 서버 실행 (포트 8080)
./gradlew bootRun
```

- 시세 서버 주소는 환경변수 `PRICE_BASE_URL`로 바꿀 수 있습니다. (기본값 `http://localhost:9091`)

## 테스트

```bash
./gradlew test
```

- `PortfolioCalculationTest`에서 매입가 고정, 반올림 규칙, 통화 혼재 계산을 검증합니다.

## 주요 설계 결정

| 주제 | 결정 |
|---|---|
| 상품 데이터 정리 | CSV 원본은 그대로 두고, 읽을 때 정리합니다. 중복 코드는 먼저 나온 행을 쓰고 경고 로그를 남깁니다. 통화 표기(KRW, krw, 원 등)와 날짜 형식을 통일합니다. |
| 검색, 정렬, 필터 | JPA Specification으로 조건을 조합합니다. |
| 시세 연동 | 토큰을 보관해 재사용하고, 401을 받으면 한 번 재발급한 뒤 다시 요청합니다. |
| 시세 갱신 | 30초 주기 스케줄러를 사용합니다. 갱신에 실패하면 마지막으로 성공한 값을 유지하고, `priceStale: true`로 오래된 값임을 표시합니다. 이 필드는 명세에 없는 추가 필드라 `docs/api.md`에 기록했습니다. |
| 포트폴리오 계산 | 매입 시점의 가격과 환율을 보유 종목에 저장해 고정합니다. 금액은 원 단위 미만을 버리고, 수익률은 HALF_UP으로 반올림합니다. |

- 빈틈 G1~G5에 대한 결정과 이유는 `docs/assumptions.md`에 있습니다. 평가 기준표 B 영역에서 상 수준에 해당하는 예시입니다.

## 검증 결과

- 가상 시세 서버(관리자 API 포함)와 정답 서버를 함께 실행하고 curl로 확인했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 상품 검색 | 부분 일치 검색 정상 동작 (예: 성장으로 검색하면 7건) |
| S2 상품 정렬 | 상장일 오름차순 정렬 확인 |
| S3 상품 필터 | 유형과 통화 AND 조건 확인 (ETF, KRW 조건으로 35건) |
| S4 데이터 정리 | 130행에서 고유 코드 120개. 중복 10건을 경고 로그로 모두 감지 |
| S5 포트폴리오 CRUD | 생성 201, 없는 상품 400 `UNKNOWN_PRODUCT`, 없는 ID 404 |
| S6 목록 정렬 | `sort=name,asc` 등 정상 동작 |
| S7 매입가 고정 | 시세를 15,000으로 바꿔도 매입가 10,000 유지, 평가금액만 바뀜 (자동화 테스트) |
| S8 계산과 반올림 | 30,099.9999는 30,099로 버림, 수익률 0.33 (HALF_UP, 자동화 테스트) |
| S10 토큰 만료 | 관리자 API로 토큰을 강제 만료시키면, 다음 갱신에서 401을 받고 재발급한 뒤 다시 요청해 성공 |
| S11 시세 서버 장애 | 관리자 API로 `ALWAYS_503`을 설정해도 상품 조회, 포트폴리오 생성과 조회가 모두 200 응답. `priceStale: true` 표시 |
| G1 구성 변경 | 수량 증가, 종목 삭제, 종목 추가가 섞인 `PUT` 후 총원가가 기대값과 일치 |
| G3 통화 혼재 | 원화와 달러 상품이 섞인 포트폴리오의 매입금액이 매입 시점 환율로 환산됨 (자동화 테스트) |

## 패키지 구조

```
com.practicefintech.portfolio
├── PortfolioApplication.java
├── common/
│   ├── entity/        # Product, Portfolio, Holding (JPA 엔티티)
│   ├── enums/          # CurrencyCode, ProductType
│   ├── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
│   ├── config/          # PriceClientProperties, ProductsProperties
│   └── dto/             # PageResponse (공통 페이지 응답)
└── domain/
    ├── product/
    │   ├── controller/
    │   ├── service/      # ProductService, ProductCsvLoader(데이터 정리, 가정 G5), ProductSpecifications
    │   ├── repository/
    │   └── model/response/
    ├── portfolio/
    │   ├── controller/
    │   ├── service/      # PortfolioService (계산, 가정 G1~G4 반영)
    │   ├── repository/
    │   └── model/
    │       ├── request/
    │       └── response/
    └── price/
        ├── service/      # PriceClient(토큰 보관), PriceRefreshScheduler, PriceSnapshotStore
        └── model/dto/     # 가상 시세 서버 응답 형식(External*)
```

- DTO는 record 대신 Lombok(`@Getter` 등)을 사용한 일반 클래스로 작성했습니다.
