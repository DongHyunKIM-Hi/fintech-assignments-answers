# 과제 2. 결제 한도 차감 + 수수료 정책 — 정답 서버

수강생에게 주는 뼈대 프로젝트(`starter-project/`)를 완성한 참고 구현입니다.
**정답은 하나가 아닙니다.** 예를 들어 전역 락으로 모든 요청을 줄 세우는 방식도 정확성은 통과하는 정당한 대안입니다 (기준표 D2 참고).

## 실행

```bash
./gradlew bootRun   # 포트 8080
```

## 테스트

```bash
./gradlew test
```

| 테스트 | 내용 |
|---|---|
| `PaymentServiceConcurrencyTest` (5개) | 동시 중복(같은 사용자·다른 사용자), 한도 경합, 동시 취소, 승인·취소 혼합 |
| `FeePolicyTest` (19개) | 수수료 표의 경계값과 규칙 조합. **기준표 A12의 확인 벡터로 그대로 사용** |

두 테스트는 기준표 B 영역(테스트 작성) "상" 수준의 예시이기도 합니다.

## 핵심 설계 결정

| 주제 | 결정 |
|---|---|
| 같은 결제 ID 중복 방지 | 결제 ID마다 락을 걸어 "조회 → 없으면 승인"을 한 번에 처리. 사용자와 무관하게 걸어서 다른 사용자가 같은 ID를 써도 막힘 |
| 한도 경합 | 그 안에서 사용자 단위 락으로 한도 읽기·쓰기를 보호 |
| 교착 상태 방지 | 잠금 순서를 항상 "결제 ID 락 → 사용자 락"으로 고정 |
| 거절된 ID 재사용 | 거절 시 아무것도 저장하지 않고 락을 빠져나오므로, 별도 해제 코드 없이 같은 ID로 다시 요청 가능 |
| 수수료 계산 | 버그 4개가 심어진 레거시 계산기를 표 순서(면제 → 수수료율 → 고액 감면 → 절사·최소 → 상한)대로 다시 작성. `double` 대신 정수(bp) 계산 |

자세한 이유는 `docs/answer.md`(서술 문제 작성 예시, 기준표 C "상" 수준)에 있습니다.

## 검증 결과

서버를 직접 띄워 curl로 확인했고, 위 자동화 테스트로도 검증했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 정상 승인 | 200, `fee: 250`, `remainingLimit: 950000` (GOLD 국내 50,000원) |
| S2 순차 중복 | 409 `DUPLICATE_PAYMENT` |
| S3 동시 중복 (같은 사용자, 10건) | 정확히 1건 200, 9건 409, 사용액 1건분 |
| S4 동시 중복 (다른 사용자 10명) | 정확히 1건 200, 9건 409 |
| S5 동시 한도 경합 (400,000원 × 3건) | 정확히 2건 200, 1건 422, 사용액 800,000 |
| S6 한도 경계 | 1,000,000원 승인 → 1원 422 → 취소 → 거절된 ID 재요청 200 |
| S7 입력 오류 | 승인·수수료 API 모두 400 |
| S8 취소 | 200 + 한도 복원, 없는 ID는 404 |
| S9 동시 취소 (10건) | 정확히 1건 200, 9건 409, 한도 1회만 복원 |
| S10 취소 후 재승인 | 409 |
| S11 승인·취소 혼합 동시 | 최종 사용액이 0 또는 200,000 (불변 조건 유지) |
| S12 수수료 표 | 가이드 예시 전부 일치 |

## 구조

```
com.practicefintech.paymentlimit
├── PaymentLimitApplication.java
├── common/
│   ├── entity/        # PaymentRecord — 수정 금지 (뼈대 그대로)
│   ├── enums/          # Grade, PayType, PaymentState, ErrorCode
│   └── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
└── domain/
    ├── payment/
    │   ├── controller/
    │   ├── service/     # PaymentService(동시성 처리 지점)
    │   ├── repository/   # LimitStore — 수정 금지 (뼈대 그대로)
    │   └── model/
    │       ├── request/
    │       └── response/
    └── fee/
        ├── service/     # FeePolicy — 레거시 계산기를 표 기준으로 다시 쓴 버전
        └── model/response/
```

nbcam-plus 스타일(패키지 구조)에 맞춰 재구성했습니다. DTO는 record 대신 Lombok(`@Getter` 등)을 쓴 일반 클래스입니다. 동작은 이전과 동일합니다.

## 뼈대와 달라진 점

- `payment.PaymentController`: `TODO`를 실제 구현으로 교체, `PaymentService`에 위임
- `payment.PaymentService`: 새로 추가. 결제 ID 락 + 사용자 락
- `fee.FeePolicy`: 새로 추가. 뼈대의 `fee.LegacyFeeCalculator`(버그 있는 레거시)를 대체
- 패키지 이름이 뼈대(`com.practice.paymentlimit`)와 다릅니다. 정답 코드만의 차이이며 채점과는 무관합니다.
