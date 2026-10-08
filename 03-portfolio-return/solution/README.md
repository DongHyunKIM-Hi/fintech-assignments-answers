# 과제 3. 가상 포트폴리오 수익률 서비스 — 정답 서버

수강생에게 주는 빈 시작 프로젝트(`starter-project/`) 위에 모든 도메인 코드를 새로 작성한 참고 구현입니다.
**정답은 하나가 아닙니다.** 특히 명세 밖의 빈틈(G1~G5)은 다른 결정도 근거만 있으면 인정합니다 (기준표 B 영역).

## 실행

```bash
# 1) 가상 시세 서버 (포트 9091)
docker run -p 9091:9091 ghcr.io/donghyunkim-hi/portfolio-price-server:1.0.0

# 2) 정답 서버 (포트 8080)
./gradlew bootRun
```

시세 서버 주소는 환경변수 `PRICE_BASE_URL`로 바꿀 수 있습니다 (기본 `http://localhost:9091`).

## 테스트

```bash
./gradlew test
```

`PortfolioCalculationTest`가 매입가 고정, 반올림 규칙, 통화 혼재 계산을 검증합니다.

## 핵심 설계 결정

| 주제 | 결정 |
|---|---|
| 상품 데이터 정리 | CSV 원본은 그대로 두고 읽을 때 정리. 중복 코드는 먼저 나온 행을 쓰고 경고 로그를 남김. 통화 표기(KRW/krw/원 등)와 날짜 형식을 통일 |
| 검색·정렬·필터 | JPA Specification으로 조건을 조합 |
| 시세 연동 | 토큰을 보관해 재사용하고, 401을 받으면 한 번 재발급 후 재시도 |
| 시세 갱신 | 30초 스케줄러. 갱신에 실패하면 마지막 성공 값을 유지하고 `priceStale: true`로 오래된 값임을 표시 (계약 밖 추가 필드, `docs/api.md`에 기록) |
| 포트폴리오 계산 | 매입 시점의 가격·환율을 보유 종목에 저장해 고정. 금액은 절사, 수익률은 HALF_UP 반올림 |
| 동시 수정 보호 | `PortfolioService`에 포트폴리오 ID 단위 락(`portfolioLocks`)을 걸어, 같은 포트폴리오를 거의 동시에 두 번 PUT/DELETE/조회할 때 생길 수 있는 lost update를 줄임 |

빈틈 G1~G5에 대한 결정과 이유는 `docs/assumptions.md`에 있습니다 (기준표 B "상" 수준 예시).

> [!NOTE]
> **튜터 전용 참고 (2026-10-08 추가)**: 원래 `PortfolioService`의 `create/update/delete`에는 락이 전혀 없었습니다. 과제 4의 동시성 버그를 전수 재검사하던 중 함께 발견한 사항으로, 같은 포트폴리오를 거의 동시에 두 번 PUT하면 한쪽 수정이 조용히 덮여 사라지는 "lost update"가 이론상 가능했습니다. **이 과제의 채점 시나리오(S1~S11)에는 "같은 포트폴리오 동시 수정" 테스트가 없고, 서버가 멈추거나 예외가 나는 문제도 아니라서 원래는 기록만 하고 넘어가려 했습니다.** 다만 안전을 더 챙기기 위해 다른 과제들처럼 ID 단위 락을 추가했습니다. 단, 이 서비스는 JPA(`@Transactional`)를 쓰기 때문에 과제 2·4의 인메모리 저장소 락과 완전히 같은 수준의 보장은 아닙니다 — `synchronized` 블록은 메서드 본문이 끝나는 순간 풀리는데, 실제 DB 커밋은 Spring이 그 이후(트랜잭션 AOP)에 처리하기 때문에, 락 해제와 커밋 사이에 이론상 아주 짧은 틈이 남습니다. 완전히 틈을 없애려면 `Portfolio` 엔티티에 `@Version`을 추가하는 낙관적 락(optimistic locking)까지 가야 하는데, 이는 엔티티·예외 처리 구조를 바꾸는 더 큰 변경이라 이번에는 적용하지 않았습니다. 실무 위험은 이미 매우 낮다고 판단합니다(동시 접근 자체가 드물고, 틈도 매우 짧음).

## 검증 결과

가상 시세 서버(admin API 포함)와 정답 서버를 함께 띄우고 curl로 확인했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 상품 검색 | 부분 일치 정상 동작 (예: "성장" 검색 시 7건) |
| S2 상품 정렬 | 상장일 오름차순 정렬 확인 |
| S3 상품 필터 | 유형 + 통화 AND 결합 확인 (ETF + KRW 35건) |
| S4 데이터 정리 | 130행 → 유일 코드 120개, 중복 10건 경고 로그로 정확히 감지 |
| S5 포트폴리오 CRUD | 생성 201, 없는 상품 400 `UNKNOWN_PRODUCT`, 없는 ID 404 |
| S6 목록 정렬 | `sort=name,asc` 등 정상 |
| S7 매입 시점 고정 | 시세를 15,000으로 바꿔도 매입가 10,000 유지, 평가금액만 변경 (자동화 테스트) |
| S8 계산·반올림 | 30,099.9999 → 절사 30,099, 수익률 0.33 HALF_UP (자동화 테스트) |
| G3 통화 혼재 | 원화 + 달러 포트폴리오의 매입금액이 매입 시점 환율로 정확히 환산 (자동화 테스트) |
| G1 구성 변경 | 수량 증가·삭제·신규 추가가 섞인 `PUT` 후 총원가가 기대값과 정확히 일치 |
| S10 토큰 만료 | admin으로 토큰 강제 만료 → 다음 갱신에서 401 감지 후 재발급·재시도 성공 |
| S11 시세 서버 장애 | admin으로 `ALWAYS_503` 설정 → 상품 조회·포트폴리오 생성·조회 모두 200 유지, `priceStale: true` 표시 |

## 구조

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
    │   ├── service/      # PortfolioService — 계산(가정 G1~G4가 반영된 지점)
    │   ├── repository/
    │   └── model/
    │       ├── request/
    │       └── response/
    └── price/
        ├── service/      # PriceClient(토큰 캐시), PriceRefreshScheduler, PriceSnapshotStore
        └── model/dto/     # 가상 시세 서버의 External* 응답 형식
```

nbcam-plus 스타일(패키지 구조)에 맞춰 재구성했습니다. DTO는 record 대신 Lombok(`@Getter` 등)을 쓴 일반 클래스입니다. 동작은 이전과 동일합니다.
