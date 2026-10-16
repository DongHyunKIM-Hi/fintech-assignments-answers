# 백엔드 실습 과제 정답 코드 및 평가 기준표 (튜터용)

> [!CAUTION]
> - 튜터용 자료입니다.
> - 정답 코드와 평가 기준표가 들어 있으므로, 수강생에게 이 저장소의 링크를 안내하지 마세요.

## 과제 목록

| 과제 | 수강생 저장소 (공개) | 이 저장소의 폴더 |
|---|---|---|
| 1. 환불 요청 중계 서버 | [refund-relay-assignment](https://github.com/DongHyunKIM-Hi/refund-relay-assignment) | [`01-refund-relay/`](01-refund-relay/) |
| 2. 결제 한도 차감 및 수수료 정책 | [payment-limit-fee-assignment](https://github.com/DongHyunKIM-Hi/payment-limit-fee-assignment) | [`02-payment-limit-fee/`](02-payment-limit-fee/) |
| 3. 가상 포트폴리오 수익률 서비스 | [portfolio-return-assignment](https://github.com/DongHyunKIM-Hi/portfolio-return-assignment) | [`03-portfolio-return/`](03-portfolio-return/) |
| 4. 쿠폰 사용과 회의실 예약 (동시성 워밍업) | [concurrency-warmup-assignment](https://github.com/DongHyunKIM-Hi/concurrency-warmup-assignment) | [`04-concurrency-warmup/`](04-concurrency-warmup/) |

- 수강생용 요구사항 요약 PDF는 각 수강생 저장소의 `docs/requirements-overview.pdf`에 있습니다.

## 과제별 폴더 구성

- `평가기준표.md`: 튜터 채점 기준입니다.
  - 배점, 수준별 기준, 확인 방법, 치명 오류, 기록 양식을 담고 있습니다.
  - 4개 과제 모두 100점 만점입니다.
- `solution/`: 정답 코드입니다.
  - 기술 스택: Java 17, Spring Boot 4.1.1, Gradle
  - 실행 방법과 설계 결정은 `solution/README.md`에 있습니다.

### 패키지 구조

- 4개 과제의 정답 코드는 같은 패키지 구조를 사용합니다.
  - `common/{entity,enums,exception,config,utils,dto}`
  - `domain/<기능>/{controller,service,repository,model/{request,response,dto}}`
- DTO는 record 대신 Lombok(`@Getter`, `@AllArgsConstructor` 등)을 사용한 일반 클래스로 작성했습니다.
- 수강생에게 시작 코드(starter-project)를 제공하는 과제 2, 4도 같은 구조를 사용합니다.

## 과제별 요약

| | 과제 1 | 과제 2 | 과제 3 | 과제 4 (워밍업) |
|---|---|---|---|---|
| 평가 역량 | 설계력 | 정확성과 설명 능력 | 자율 구현과 명세 해석 | 동시성 제어 범위 설정 |
| 주요 과제 | 느리거나 실패하는 외부 결제대행사 연동, 이중 환불 방지 | 동시 결제 시 한도 차감과 중복 처리, 레거시 수수료 코드 정리 | 결함 있는 데이터 정리, 토큰 만료와 장애가 있는 외부 시세 연동, 명세에 없는 상황 처리 | 쿠폰 중복 사용과 예약 시간 겹침 방지, 락 범위를 사용자 또는 회의실 단위로 좁히기 |
| 제공 가상 서버 | 결제대행사 (`ghcr.io/donghyunkim-hi/pg-mock:1.0.0`) | 없음 | 시세 서버 (`ghcr.io/donghyunkim-hi/portfolio-price-server:1.0.0`) | 없음 |
| 정답 코드 테스트 | 1개 (실행 확인) | 24개 (동시성 5, 수수료 19) | 3개 (계산 검증) | 10개 (동시성 9, 실행 확인 1) |

- 4개 정답 코드 모두 `./gradlew test` 통과를 확인했습니다. (2026-10-16)

## 채점 공통 원칙

- 정답은 하나가 아닙니다.
  - 정답 코드는 좋은 답 중 하나의 예시입니다.
  - 평가 기준표는 결과뿐 아니라 설계를 선택한 근거도 평가합니다.
- 판정이 애매하면 낮은 점수를 주지 말고 **판정 보류**로 기록한 뒤 이유를 남깁니다.
- 기능 확인은 튜터가 수강생 서버를 직접 실행하고 요청을 보내는 방식으로 진행합니다.
  - 기대 동작이 헷갈리면 정답 코드를 실행해 같은 요청을 보내 보세요.
