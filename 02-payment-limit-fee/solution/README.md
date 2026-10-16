# 과제 2. 결제 한도 차감과 수수료 정책 정답 코드

- 수강생에게 제공한 시작 프로젝트(`starter-project/`)를 완성한 참고 구현입니다.
- 정답은 하나가 아닙니다. 예를 들어 전역 락으로 모든 요청을 차례로 처리해도 정확성 시나리오는 통과합니다. (평가 기준표 D2 참고)

## 실행 방법

```bash
./gradlew bootRun   # 포트 8080
```

## 테스트

```bash
./gradlew test
```

| 테스트 | 내용 |
|---|---|
| `PaymentServiceConcurrencyTest` (5개) | 같은 사용자의 동시 중복 요청, 다른 사용자의 동시 중복 요청, 한도 경합, 동시 취소, 승인과 취소 혼합 |
| `FeePolicyTest` (19개) | 수수료 표의 경계값과 규칙 조합. 평가 기준표 A12의 확인 벡터로도 사용합니다. |

두 테스트는 평가 기준표 B 영역(테스트 작성)에서 상 수준에 해당하는 예시입니다.

## 주요 설계 결정

| 주제 | 결정 |
|---|---|
| 같은 결제 ID 중복 방지 | 결제 ID마다 락을 걸고, 그 안에서 기존 결제를 조회한 뒤 없으면 승인합니다. 사용자와 관계없이 결제 ID 기준으로 잠그므로 다른 사용자가 같은 ID를 보내도 막힙니다. |
| 한도 경합 | 결제 ID 락 안에서 사용자 단위 락을 한 번 더 걸어 한도 읽기와 쓰기를 보호합니다. |
| 교착 상태 방지 | 락은 항상 결제 ID 락, 사용자 락 순서로 겁니다. |
| 거절된 ID 재사용 | 거절할 때는 아무것도 저장하지 않고 락을 빠져나옵니다. 따로 해제하는 코드 없이 같은 ID로 다시 요청할 수 있습니다. |
| 수수료 계산 | 버그 4개를 일부러 넣어 둔 기존 계산기를 표의 적용 순서(면제, 수수료율, 고액 감면, 버림과 최소 수수료, 최대 수수료)대로 다시 작성했습니다. `double` 대신 정수(bp)로 계산합니다. |

- 각 결정의 이유는 `docs/answer.md`에 있습니다. 서술 문제의 작성 예시이며, 평가 기준표 C 영역에서 상 수준에 해당합니다.

## 검증 결과

- 서버를 직접 실행해 curl로 확인했고, 위 테스트로도 검증했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 정상 승인 (GOLD 국내 50,000원) | 200, `fee: 250`, `remainingLimit: 950000` |
| S2 순차 중복 | 409 `DUPLICATE_PAYMENT` |
| S3 동시 중복 (같은 사용자, 10건) | 200 1건, 409 9건, 사용액은 1건 금액만큼 |
| S4 동시 중복 (다른 사용자 10명) | 200 1건, 409 9건 |
| S5 동시 한도 경합 (400,000원 3건) | 200 2건, 422 1건, 사용액 800,000 |
| S6 한도 경계 | 1,000,000원 승인, 1원 요청 422, 취소, 거절된 ID로 다시 요청 200 |
| S7 입력 오류 | 승인 API와 수수료 API 모두 400 |
| S8 취소 | 200과 한도 복원, 없는 ID는 404 |
| S9 동시 취소 (10건) | 200 1건, 409 9건, 한도는 1번만 복원 |
| S10 취소 후 재승인 | 409 |
| S11 승인과 취소 동시 요청 | 최종 사용액이 0 또는 200,000 (불변 조건 유지) |
| S12 수수료 표 | README의 계산 예시와 모두 일치 |

## 패키지 구조

```
com.practicefintech.paymentlimit
├── PaymentLimitApplication.java
├── common/
│   ├── entity/        # PaymentRecord (수정 금지, 시작 프로젝트와 동일)
│   ├── enums/          # Grade, PayType, PaymentState, ErrorCode
│   └── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
└── domain/
    ├── payment/
    │   ├── controller/
    │   ├── service/     # PaymentService (동시성 처리)
    │   ├── repository/   # LimitStore (수정 금지, 시작 프로젝트와 동일)
    │   └── model/
    │       ├── request/
    │       └── response/
    └── fee/
        ├── service/     # FeePolicy (기존 계산기를 규칙 표 기준으로 다시 작성)
        └── model/response/
```

- DTO는 record 대신 Lombok(`@Getter` 등)을 사용한 일반 클래스로 작성했습니다.

## 시작 프로젝트와 달라진 점

- `PaymentController`: `TODO`를 구현으로 바꾸고, 처리는 `PaymentService`에 맡깁니다.
- `PaymentService`: 새로 추가했습니다. 결제 ID 락과 사용자 락을 사용합니다.
- `FeePolicy`: 새로 추가했습니다. 시작 프로젝트의 `LegacyFeeCalculator`(버그가 있는 기존 계산기)를 대신합니다.
- 패키지 이름이 시작 프로젝트(`com.practice.paymentlimit`)와 다릅니다. 정답 코드에만 해당하며 채점과는 관계없습니다.
