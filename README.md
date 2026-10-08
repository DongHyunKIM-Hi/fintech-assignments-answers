# 백엔드 실습 과제 — 정답 코드 · 평가 기준표 (튜터용)

> [!CAUTION]
> **비공개 저장소입니다.** 정답 코드와 평가 기준표가 들어 있으므로 수강생에게 공유하지 마세요.

## 과제 목록

| 과제 | 수강생 저장소 (공개) | 이 저장소의 폴더 |
|---|---|---|
| 1. 환불 요청 중계 서버 | [refund-relay-assignment](https://github.com/DongHyunKIM-Hi/refund-relay-assignment) | [`01-refund-relay/`](01-refund-relay/) |
| 2. 결제 한도 차감 + 수수료 정책 | [payment-limit-fee-assignment](https://github.com/DongHyunKIM-Hi/payment-limit-fee-assignment) | [`02-payment-limit-fee/`](02-payment-limit-fee/) |
| 3. 가상 포트폴리오 수익률 서비스 | [portfolio-return-assignment](https://github.com/DongHyunKIM-Hi/portfolio-return-assignment) | [`03-portfolio-return/`](03-portfolio-return/) |

수강생용 요구사항 요약 PDF는 각 수강생 저장소의 `docs/requirements-overview.pdf`에 있습니다.

## 과제마다 들어 있는 것

| 파일·폴더 | 내용 |
|---|---|
| `평가기준표.md` | 튜터 채점 기준 (배점, 수준별 기준, 확인 방법, 치명 오류, 기록 양식) |
| `solution/` | 정답 서버 (Java 17 · Spring Boot 4.1.1 · Gradle). 실행 방법과 설계 결정은 `solution/README.md` |

## 과제 한눈에 보기

| | 과제 1 | 과제 2 | 과제 3 |
|---|---|---|---|
| 보는 능력 | 설계력 | 정확성과 설명 | 자율 구현과 명세 해석 |
| 핵심 난제 | 느리고 실패하는 외부 결제대행사, 이중 환불 방지 | 동시 결제의 한도·중복 처리, 레거시 수수료 코드 정리 | 결함 있는 데이터 정리, 토큰·장애가 있는 외부 시세 연동, 명세 밖의 빈틈 |
| 제공 가상 서버 | 결제대행사 (`ghcr.io/donghyunkim-hi/pg-mock:1.0.0`) | 없음 | 시세 서버 (`ghcr.io/donghyunkim-hi/portfolio-price-server:1.0.0`) |
| 정답 서버 테스트 | 1개 (기동 확인) | 24개 (동시성 5, 수수료 19) | 3개 (계산 검증) |

정답 서버 3개는 모두 `./gradlew test` 통과를 확인했습니다 (2026-10-08).

## 공통 원칙

- **정답은 하나가 아닙니다.** 정답 서버는 여러 좋은 답 중 하나이며, 기준표는 결과뿐 아니라 **선택의 근거**를 봅니다.
- 판정이 애매하면 낮은 점수가 아니라 **"판정 보류"**로 기록하고 이유를 남깁니다.
- 기능 확인은 튜터가 서버를 직접 띄워 요청을 보내는 방식입니다. 기대 동작이 헷갈리면 정답 서버를 띄워 같은 요청을 보내 보세요.
