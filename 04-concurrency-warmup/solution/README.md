# 과제 4. 쿠폰 사용과 회의실 예약 (동시성 워밍업) — 정답 서버

수강생에게 주는 뼈대 프로젝트(`starter-project/`)의 `TODO` 4개를 완성한 참고 구현입니다.
**정답은 하나가 아닙니다.** 예를 들어 전역 락 하나로 모든 요청을 줄 세워도 정확성(A)은 만점입니다. 다만 락의 범위(B1)에서 감점됩니다.

## 실행

```bash
./gradlew bootRun   # 포트 8080
```

가상 서버는 없습니다. 서버 하나만 띄우면 됩니다.

## 락을 어디에 어떻게 걸었나

수강생 README에서 요구하는 "3~4줄 설명"의 예시이기도 합니다 (기준표 B3).

- **쿠폰**: `CouponService`에서 **사용자 ID마다 락**을 걸고, 그 안에서 "가장 할인이 큰 쿠폰 찾기 → 삭제"를 한 번에 처리합니다. 같은 사용자의 요청은 하나씩, 다른 사용자끼리는 동시에 처리됩니다.
- **예약**: `ReservationService`에서 **방 ID마다 락**을 걸고, 그 안에서 "겹치는 예약 확인 → 저장"을 한 번에 처리합니다. 같은 방은 하나씩, 다른 방끼리는 동시에 처리됩니다.
- **저장소 함정 대응**: 쿠폰 저장소(수정 금지)는 내부 리스트를 그대로 돌려줍니다. 그래서 락 안에서 바로 복사본을 만들어 씁니다.
- **발급도 잠급니다**: `CouponService.issue()`도 `use()`와 같은 사용자 단위 락을 씁니다. 발급만 잠그지 않으면 같은 사용자에게 동시에 여러 번 발급 요청이 올 때 내부 리스트가 깨질 수 있습니다 (2026-10-08 발견·수정, 아래 "튜터 전용 참고" 참고).

> [!NOTE]
> **튜터 전용 참고 (2026-10-08 수정)**: `CouponStore`, `ReservationStore`(둘 다 수정 금지 제공 파일)의 내부 맵을 `HashMap`에서 `ConcurrentHashMap`으로 바꿨습니다. 서로 다른 두 사용자/방에 **처음으로** 동시에 접근하면 `HashMap`의 구조적 변경이 경쟁해 드물게(약 20회 중 1회 미만) `ConcurrentModificationException`이 날 수 있었습니다. 또한 `CouponService.issue()`에 락이 없어서, **같은 사용자**가 동시에 여러 번 쿠폰을 발급받으면 내부 리스트(`ArrayList`)에 대한 보호되지 않은 동시 쓰기가 일어나 **쿠폰이 조용히 사라질 수 있었습니다** (50개 스레드로 150회 반복 시 42회, 약 28% 확률로 재현— 이쪽이 더 심각했습니다). 두 수정 모두 공개 수강생 저장소(뼈대)와 이 정답 저장소에 반영했고, 수정 전/후 모두 전용 스트레스 테스트(동시 150~300회 반복)로 재현·검증했습니다. 저장소가 반환하는 리스트를 방어적으로 복사해야 하는 "T1 함정"은 그대로 유지됩니다 — 바뀐 것은 저장소 내부 맵의 구조적 안전성뿐입니다.

## 테스트

```bash
./gradlew test
```

| 테스트 | 확인하는 것 |
|---|---|
| `CouponServiceConcurrencyTest` (4개) | 쿠폰 2장·동시 2건 모두 성공, 쿠폰 1장·동시 5건 중 1건만 성공, 다른 사용자끼리 서로 막히지 않음, 할인이 가장 큰 쿠폰 선택 |
| `ReservationServiceConcurrencyTest` (5개) | 같은 방·겹치는 시간 동시 5건 중 1건만 성공, 경계만 맞닿은 시간은 둘 다 성공, 다른 방·같은 시간은 둘 다 성공, 순차 겹침은 409, 사용자별 조회 |
| `WarmupApplicationTests` (1개) | 서버 기동 확인 |

10개 모두 통과합니다. 서버를 직접 띄워 curl로 순차 시나리오(정상 사용, 쿠폰 없음, 정상 예약, 순차 겹침, 잘못된 입력)도 확인했습니다.

## 구조

```
com.practicefintech.warmup
├── WarmupApplication.java
├── common/
│   ├── entity/        # Coupon, Reservation — 수정 금지 (뼈대 그대로)
│   ├── enums/          # CouponType(수정 금지), ErrorCode
│   └── exception/      # ApiException, ErrorResponse, GlobalExceptionHandler
└── domain/
    ├── coupon/
    │   ├── controller/
    │   ├── service/      # CouponService(사용자 단위 락)
    │   ├── repository/    # CouponStore — 수정 금지 (뼈대 그대로)
    │   └── model/
    │       ├── request/
    │       └── response/
    └── reservation/
        ├── controller/
        ├── service/       # ReservationService(방 단위 락)
        ├── repository/     # ReservationStore — 수정 금지 (뼈대 그대로)
        └── model/
            ├── request/
            ├── response/
            └── dto/        # RoomReservation (내부 전달용)
```

패키지 이름이 뼈대(`com.practice.warmup`)와 다릅니다. 정답 코드만의 차이이며 채점과는 무관합니다. nbcam-plus 스타일(패키지 구조)에 맞춰 재구성했습니다. DTO는 record 대신 Lombok(`@Getter` 등)을 쓴 일반 클래스입니다. 동작은 이전과 동일합니다.
