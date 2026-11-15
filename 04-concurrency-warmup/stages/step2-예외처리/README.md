# 2단계 — 예외 처리와 시나리오

> 해설 영상용 **중간 단계** 코드입니다. 최종본은 `../../solution/`에 있습니다.
> 단계 전체 설명과 실측 결과는 `../README.md`를 보세요.

## 1단계에서 추가한 것

- `GlobalExceptionHandler` — 에러 응답을 `{"code","message"}` 형식으로 통일
- 쿠폰
  - 보유 쿠폰 없음 → **404** `NO_COUPON_AVAILABLE`
  - `amount`가 0 이하 → **400** `INVALID_REQUEST`
  - 잘못된 `type` → **400** `INVALID_REQUEST`
- 예약
  - 시간 형식 오류, `from >= to` → **400** `INVALID_REQUEST`
  - 시간 겹침 → **409** `TIME_CONFLICT` (제공된 `Reservation.overlaps()` 사용)
  - 끝과 시작이 맞닿기만 한 경우는 겹치지 않음 → **201**

## 여전히 하지 않은 것

**동시성 제어가 없습니다.** 이 단계의 목적은 *혼자 보낼 때는 완벽한 서버*를 만드는 것입니다.

두 서비스에 주석으로 표시해 두었습니다.

- `CouponService` — `// 여기서 "찾기"와 "삭제" 사이에 다른 요청이 끼어들 수 있다`
- `ReservationService` — `// 여기서 "겹침 확인"과 "저장" 사이에 다른 요청이 끼어들 수 있다`

## 깨뜨려 보기

```bash
# 쿠폰 1장에 동시 5건 — 1건만 200이어야 하는데 전부 200이 됩니다
curl -s -X POST localhost:8080/api/users/u3/coupons -H 'Content-Type: application/json' -d '{"type":"PERCENT_15"}'
for i in 1 2 3 4 5; do curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/users/u3/coupons/use \
  -H 'Content-Type: application/json' -d '{"amount":10000}' & done; wait

# 같은 회의실·같은 시간 동시 5건 — 1건만 201이어야 하는데 여러 건 201이 됩니다
ROOM=R8-$RANDOM; for i in 1 2 3 4 5; do curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/reservations \
  -H 'Content-Type: application/json' -d "{\"roomId\":\"$ROOM\",\"userId\":\"U$i\",\"from\":\"09:00\",\"to\":\"10:00\"}" & done; wait
```

쿠폰은 저장소 지연(50\~100ms) 때문에 거의 매번 깨집니다. 예약은 지연이 없어 20회 중 15\~18회 깨지므로, 한 번에 안 되면 2\~3회 반복하세요.

## 실행

```bash
./gradlew bootRun   # 포트 8080
```

## 다음 단계

3단계(`../../solution/`)에서 락의 범위를 설계해 해결합니다. 바뀌는 것은 서비스 2개뿐입니다.
