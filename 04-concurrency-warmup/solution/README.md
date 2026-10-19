# 과제 4. 쿠폰 사용과 회의실 예약 (동시성 워밍업) 정답 코드

- 지원자에게 제공한 시작 프로젝트(`starter-project/`)의 `TODO` 4개를 완성한 참고 구현입니다.
- 정답은 하나가 아닙니다. 예를 들어 전역 락 하나로 모든 요청을 차례로 처리해도 정확성(A)은 만점입니다. 다만 락의 범위(B1)에서 감점됩니다.

## 실행 방법

```bash
./gradlew bootRun   # 포트 8080
```

- 가상 서버는 없습니다. 정답 서버만 실행하면 됩니다.

## 락 설계

지원자 README에서 요구하는 3~4줄 설명의 예시입니다. (평가 기준표 B3)

- **쿠폰**: `CouponService`에서 사용자 ID마다 락을 걸고, 그 안에서 할인액이 가장 큰 쿠폰을 찾아 삭제하는 일을 한 번에 처리합니다. 같은 사용자의 요청은 하나씩, 다른 사용자의 요청은 동시에 처리됩니다.
- **예약**: `ReservationService`에서 회의실 ID마다 락을 걸고, 그 안에서 겹치는 예약을 확인하고 저장하는 일을 한 번에 처리합니다. 같은 회의실은 하나씩, 다른 회의실은 동시에 처리됩니다.
- **저장소가 돌려주는 리스트**: 쿠폰 저장소(수정 금지)는 내부 리스트를 그대로 돌려줍니다. 그래서 락 안에서 바로 복사본을 만들어 사용합니다.
- **쿠폰 발급에도 락 적용**: `CouponService.issue()`도 `use()`와 같은 사용자 단위 락을 사용합니다. 발급에 락이 없으면 같은 사용자에게 발급 요청이 동시에 여러 번 들어올 때 내부 리스트가 깨질 수 있습니다. (아래 평가자 참고 사항 참고)

> [!NOTE]
> **평가자 참고 사항 (2026-10-08 수정)**
> - `CouponStore`, `ReservationStore`(둘 다 수정 금지 파일)의 내부 맵을 `HashMap`에서 `ConcurrentHashMap`으로 바꿨습니다.
>   - 서로 다른 두 사용자나 회의실에 처음으로 동시에 접근하면 `HashMap`의 구조 변경이 겹쳐 드물게(20회 중 1회 미만) `ConcurrentModificationException`이 발생할 수 있었습니다.
> - `CouponService.issue()`에 락이 없어서, 같은 사용자가 동시에 여러 번 쿠폰을 발급받으면 보호되지 않은 `ArrayList`에 동시에 쓰게 되어 쿠폰이 예외 없이 사라질 수 있었습니다.
>   - 50개 스레드로 150회 반복했을 때 42회(약 28%) 재현되어, 이쪽이 더 심각한 문제였습니다.
> - 두 수정 모두 공개 과제 저장소(시작 프로젝트)와 이 정답 저장소에 반영했습니다. 수정 전과 후 모두 전용 스트레스 테스트(동시 요청 150~300회 반복)로 재현하고 검증했습니다.
> - 저장소가 돌려주는 리스트를 복사해서 써야 한다는 점(힌트 2의 내용)은 그대로 유지했습니다. 바뀐 것은 저장소 내부 맵의 동시 접근 안전성뿐입니다.

## 테스트

```bash
./gradlew test
```

| 테스트 | 확인하는 내용 |
|---|---|
| `CouponServiceConcurrencyTest` (4개) | 쿠폰 2장에 동시 요청 2건이면 모두 성공, 쿠폰 1장에 동시 요청 5건이면 1건만 성공, 다른 사용자끼리 서로 막히지 않음, 할인액이 가장 큰 쿠폰 선택 |
| `ReservationServiceConcurrencyTest` (5개) | 같은 회의실에 겹치는 시간으로 동시 요청 5건이면 1건만 성공, 끝과 시작만 맞닿은 시간은 둘 다 성공, 다른 회의실의 같은 시간은 둘 다 성공, 순차 요청의 시간 겹침은 409, 사용자별 조회 |
| `WarmupApplicationTests` (1개) | 서버 실행 확인 |

- 10개 테스트가 모두 통과합니다.
- 서버를 직접 실행해 curl로 순차 시나리오(정상 사용, 쿠폰 없음, 정상 예약, 순차 겹침, 잘못된 입력)도 확인했습니다.

## 패키지 구조

```
com.practicefintech.warmup
├── WarmupApplication.java
├── common/
│   ├── entity/        # Coupon, Reservation (수정 금지, 시작 프로젝트와 동일)
│   ├── enums/          # CouponType(수정 금지), ErrorCode
│   └── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
└── domain/
    ├── coupon/
    │   ├── controller/
    │   ├── service/      # CouponService (사용자 단위 락)
    │   ├── repository/    # CouponStore (수정 금지, 시작 프로젝트와 동일)
    │   └── model/
    │       ├── request/
    │       └── response/
    └── reservation/
        ├── controller/
        ├── service/       # ReservationService (회의실 단위 락)
        ├── repository/     # ReservationStore (수정 금지, 시작 프로젝트와 동일)
        └── model/
            ├── request/
            ├── response/
            └── dto/        # RoomReservation (내부 전달용)
```

- 패키지 이름이 시작 프로젝트(`com.practice.warmup`)와 다릅니다. 정답 코드에만 해당하며 채점과는 관계없습니다.
- DTO는 record 대신 Lombok(`@Getter` 등)을 사용한 일반 클래스로 작성했습니다.
