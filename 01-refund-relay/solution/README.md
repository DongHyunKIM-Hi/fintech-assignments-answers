# 과제 1. 환불 요청 중계 서버 정답 코드

- 지원자 README에 명시된 요구사항(API 형식, 수치)을 모두 지키는 참고 구현입니다.
- 정답은 하나가 아닙니다.
  - 이 코드는 평가 기준표 B 영역에서 상 수준에 해당하는 구현 예시 중 하나입니다.

## 실행 방법

```bash
# 1) 가상 결제대행사 실행 (포트 9090)
docker run -p 9090:9090 ghcr.io/donghyunkim-hi/pg-mock:1.0.0

# 2) 정답 서버 실행 (포트 8080)
./gradlew bootRun
```

- 결제대행사 주소는 환경변수 `PG_BASE_URL`로 바꿀 수 있습니다. (기본값 `http://localhost:9090`)

## 주요 설계 결정

| 주제 | 결정 |
|---|---|
| 중복 요청과 동시 요청 방지 | 주문(`orderId`) 단위 락 안에서 중복 확인, 한도 확인, 저장을 한 번에 처리합니다. `refundId`의 DB 유일 제약은 마지막 방어 수단입니다. |
| 접수와 처리 분리 | 접수 API는 저장 후 바로 202를 응답합니다. 결제대행사 호출은 별도 워커(스레드 풀)가 담당합니다. |
| 순서 처리 | 같은 주문은 앞 건이 끝나야 다음 건을 결제대행사에 보냅니다. 다른 주문은 동시에 처리합니다. |
| 재시도 | 실패하면 0.5초, 1초, 2초 간격으로 최대 3회 재시도합니다. (최대 총 4회 시도) 재시도할 때도 **항상 같은 `refundId`를 사용합니다.** |
| 시간 초과 | 결제대행사 응답을 5초까지 기다립니다. 5초가 지나면 `PG_TIMEOUT`으로 보고 같은 ID로 재시도합니다. 결제대행사는 같은 ID를 한 번만 처리하므로 이중 환불이 생기지 않습니다. |

- 설정값은 `src/main/resources/application.yml`의 `pg.*`, `worker.*`에 모여 있습니다.

## 검증 결과

- 정답 서버와 가상 결제대행사를 함께 실행하고 curl로 확인했습니다.

| 시나리오 | 결과 |
|---|---|
| S1 정상 접수 | 202, 0.08초 안에 응답 (결제대행사를 기다리지 않음) |
| S2 같은 ID, 같은 내용으로 재요청 | 200, `duplicate: true` |
| S3 같은 ID, 다른 내용 | 409 `REFUND_ID_CONFLICT` |
| S5, S6 한도 초과 | 422 `REFUND_LIMIT_EXCEEDED`, `REJECTED` 상태로 저장 |
| S7 입력 오류 | 400 `INVALID_REQUEST` |
| S9 재시도 (FAIL, FAIL, SUCCESS 순서로 응답) | `attemptCount: 3`, `COMPLETED` |
| S11 같은 주문 순차 처리 | 두 번째 건은 첫 번째 건의 응답을 받은 뒤에만 결제대행사로 전달됨 |
| S11 다른 주문 병렬 처리 | 세 주문의 요청이 같은 시각에 결제대행사로 전달됨 |
| S12 시간 초과 후 실제 처리됨 (처리에 12초 걸림, 5초에 시간 초과 후 재시도) | `attemptCount: 2`로 `COMPLETED`, 결제대행사 처리 기록 **정확히 1건** (이중 환불 없음) |
| S13 목록 조회 | 최신순 정렬 |

## 패키지 구조

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
        ├── service/     # RefundService(접수, 중복 확인, 한도 확인), RefundWorker(처리 워커)
        ├── repository/
        └── model/
            ├── request/
            └── response/
```

- DTO는 record 대신 Lombok(`@Getter` 등)을 사용한 일반 클래스로 작성했습니다.

## 참고 사항

- 테스트 코드는 서버 실행 확인용 `contextLoads` 1개뿐입니다.
  - 평가 기준표 D4(테스트 코드)로 채점하면 이 정답 코드는 중 수준입니다.
- 위 검증 결과는 서버를 직접 실행해 확인한 것입니다.
