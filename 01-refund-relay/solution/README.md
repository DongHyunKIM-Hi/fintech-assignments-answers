# 과제 1. 환불 요청 중계 서버 — 정답 서버

수강생 README의 약속(API 형식, 수치)을 모두 지키는 참고 구현입니다.
**정답은 하나가 아닙니다.** 이 코드는 평가 기준표 B 영역 "상"에 해당하는 예시 중 하나입니다.

## 실행

```bash
# 1) 가상 결제대행사 (포트 9090)
docker run -p 9090:9090 ghcr.io/donghyunkim-hi/pg-mock:1.0.0

# 2) 정답 서버 (포트 8080)
./gradlew bootRun
```

결제대행사 주소는 환경변수 `PG_BASE_URL`로 바꿀 수 있습니다 (기본 `http://localhost:9090`).

## 핵심 설계 결정

| 주제 | 결정 |
|---|---|
| 중복·동시 요청 방지 | 주문(`orderId`) 단위 락 안에서 "중복 확인 → 한도 확인 → 저장"을 한 번에 처리. `refundId` DB 유일 제약은 최종 방어선 |
| 접수와 처리 분리 | 접수 API는 저장 후 바로 202 응답. 결제대행사 호출은 별도 워커(스레드 풀)가 담당 |
| 순서 처리 | 같은 주문은 이전 건이 끝나야 다음 건을 보냄. 다른 주문은 동시에 처리 |
| 재시도 | 실패 시 0.5 → 1 → 2초 간격으로 최대 3회 재시도(총 4회 시도). 재시도도 항상 **같은 `refundId`** 사용 |
| 시간 초과 | 결제대행사 응답 대기 5초. 넘기면 `PG_TIMEOUT`으로 보고 같은 ID로 재시도 → 결제대행사가 같은 ID를 한 번만 처리하므로 이중 환불이 생기지 않음 |

설정값은 `src/main/resources/application.yml`의 `pg.*`, `worker.*`에 모여 있습니다.

## 검증 결과

정답 서버와 가상 결제대행사를 함께 띄우고 curl로 확인했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 정상 접수 | 202, 0.08초 안에 응답 (결제대행사를 기다리지 않음) |
| S2 같은 ID·같은 내용 재요청 | 200, `duplicate: true` |
| S3 같은 ID·다른 내용 | 409 `REFUND_ID_CONFLICT` |
| S5/S6 한도 초과 | 422 `REFUND_LIMIT_EXCEEDED`, `REJECTED`로 저장 |
| S7 입력 오류 | 400 `INVALID_REQUEST` |
| S13 목록 조회 | 최신순 정렬 |
| 재시도 (FAIL → FAIL → SUCCESS) | `attemptCount: 3`, `COMPLETED` |
| **시간 초과 함정** (12초 걸리는 처리 → 5초에 시간 초과 → 재시도) | `attemptCount: 2`로 `COMPLETED`, 결제대행사 처리 기록 **정확히 1건** (이중 환불 없음) |
| 같은 주문 순차 | 두 번째 건은 첫 번째 건의 응답 이후에만 결제대행사로 전달됨 |
| 다른 주문 병렬 | 세 주문이 같은 순간에 결제대행사로 전달됨 |

## 구조

```
com.practicefintech.refundrelay
├── RefundRelayApplication.java
├── common/
│   ├── entity/        # Refund (JPA 엔티티)
│   ├── enums/          # RefundStatus, FailureReason, PgCallResult
│   ├── exception/      # ErrorResponse, ApiExceptionHandler
│   ├── config/         # PgClientProperties, WorkerProperties
│   └── utils/          # PgClient (가상 결제대행사 호출)
└── domain/
    └── refund/
        ├── controller/
        ├── service/     # RefundService(접수·중복·한도), RefundWorker(처리 워커)
        ├── repository/
        └── model/
            ├── request/
            └── response/
```

nbcam-plus 스타일(패키지 구조)에 맞춰 재구성했습니다. DTO는 record 대신 Lombok(`@Getter` 등)을 쓴 일반 클래스입니다. 동작은 이전과 동일합니다.

## 알아 둘 점

테스트 코드는 서버 기동 확인(`contextLoads`) 하나뿐입니다. 기준표 D4(테스트 코드)로 채점하면 이 정답 서버는 "중"입니다. 위 검증은 서버를 직접 띄워 수행했습니다.
