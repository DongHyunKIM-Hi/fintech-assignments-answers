# 가상 시세 서버 — 관리자 API (튜터 전용)

`compose.eval.yaml`로 실행했을 때만 켜집니다. 수강생용 `compose.yaml`에서는 꺼져 있습니다.
모든 요청에 헤더 `X-Admin-Token: change-me-before-use`(= `compose.eval.yaml`의 `ADMIN_TOKEN` 값)가 필요합니다.

| API | 설명 |
|---|---|
| `POST /admin/prices` | `{"prices":{"A001":99999},"freeze":true}` — 상품별 가격 고정 |
| `POST /admin/fx` | `{"rate":1400.00,"freeze":true}` — 환율 고정 |
| `POST /admin/tokens/expire` | 발급된 모든 토큰 즉시 만료 |
| `POST /admin/failure` | `{"mode":"NONE\|ALWAYS_503\|TIMEOUT","transientFailureRate":0}` — 장애 강제 |
| `POST /admin/reset` | 가격·토큰·장애 모드·호출 로그 초기화 |
| `GET /admin/requests` | 호출 로그 조회 (토큰 재사용, 갱신 주기 확인용) |

## 참고: 공개 API (수강생 서버가 호출하는 쪽)

| API | 설명 |
|---|---|
| `POST /auth/token` | `{"clientId":"student","clientSecret":"student-secret"}` → 토큰 (120초 유효) |
| `GET /v1/prices` | (Bearer) 전 상품 시세 |
| `GET /v1/fx?pair=USD-KRW` | (Bearer) 환율 |

- 시세·환율은 30초마다 ±1%/±0.5% 이내로 랜덤 변동합니다.
- 기본 5% 확률로 503(`UNAVAILABLE`)이 옵니다. 채점할 때는 `POST /admin/failure`에 `{"mode":"NONE","transientFailureRate":0}`을 보내 끄고 시작하면 결과가 흔들리지 않습니다.
