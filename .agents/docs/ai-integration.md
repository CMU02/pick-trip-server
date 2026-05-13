# AI 일정 생성 연동

이 문서는 PickTrip 서버의 AI 일정 생성 연동 흐름과 실패 처리 전략을 정의한다.
AI 프로바이더와 프롬프트 구조는 확정 후 별도로 추가한다.

## 연동 흐름

```
클라이언트
  │
  ▼
ItineraryService
  │  1. 입력 검증 (지역, 날짜, 콘텐츠 2개 이상)
  │  2. 콘텐츠 상세 정보 로드 (운영시간, 체류 시간, 행사 기간 등)
  │  3. 여행 조건 + 콘텐츠 목록 → AI 입력 변환
  ▼
AiItineraryClient (infra/ai)
  │  4. AI 제공자 호출
  │  5. 응답 파싱 → 일정 도메인 모델로 변환
  ▼
ItineraryService
  │  6. 변환된 일정 반환 (저장 전 미리보기)
  ▼
클라이언트
```

## AI 입력에 포함되는 정보

AI 호출 전 서버가 준비해야 하는 데이터는 다음과 같다.

| 항목             | 원천                              | 비고                         |
| --------------- | --------------------------------- | ---------------------------- |
| 여행 날짜/기간   | 바구니 조건                       | 필수                         |
| 동행 조건        | 바구니 조건                       | 필수                         |
| 장소명           | `TravelContent.title`             | 필수                         |
| 카테고리         | `TravelContent.category`          | 필수                         |
| 좌표             | `latitude`, `longitude`           | 동선 계산 필수               |
| 운영시간         | `ContentDetail.useTime`           | 품질 검수 필요               |
| 휴무일           | `ContentDetail.restDate`          | 품질 검수 필요               |
| 행사 기간        | `eventStartDate`, `eventEndDate`  | 축제 배치 제약               |
| 우선순위         | `BasketItem.priority`             | MUST_VISIT 우선 배치         |
| 예상 체류 시간   | 자체 검수 데이터                  | TourAPI만으로 부족            |
| 실내/실외        | `ContentDetail.indoorOutdoor`     | 우천 대안 판단               |
| 걷기 부담        | `ContentDetail.walkingLevel`      | 부모님/아이 동반 고려         |
| 주차             | `ContentDetail.parking`           | 가족 여행 중요 필드           |

## AI 응답 처리

- AI 응답은 즉시 저장하지 않고 먼저 **도메인 모델로 파싱·검증**한다.
- 파싱 성공 시 일정 미리보기를 클라이언트에 반환한다. 저장은 별도 요청으로 처리한다.
- 각 장소 배치에는 **AI가 생성한 이유**(`reason`)를 함께 반환한다.
  - 예: "축제 운영시간이 오전 10시부터라서 1일차 오전에 배치했습니다."

## 실패 처리 전략

| 실패 유형              | HTTP 상태 | 에러 코드                        | 처리 방법                                        |
| --------------------- | --------- | -------------------------------- | ------------------------------------------------ |
| 입력 조건 부족         | 400       | `ITINERARY_INPUT_INSUFFICIENT`   | AI 호출 전 서버에서 즉시 반환                    |
| AI 응답 타임아웃       | 408       | `ITINERARY_GENERATION_TIMEOUT`   | 재시도 가능 상태로 반환. 클라이언트가 재요청     |
| AI 제공자 장애         | 502       | `ITINERARY_PROVIDER_FAILED`      | 서버 로그에 원인 기록. 클라이언트에 재시도 유도  |
| 응답 파싱 실패         | 502       | `ITINERARY_PROVIDER_FAILED`      | AI 응답 원문은 로그에만 기록. 클라이언트 노출 금지|

## 보안 규칙

- AI에 전달하는 프롬프트 전문은 로그에 남기지 않는다.
- AI 응답 원문도 운영 로그에 포함하지 않는다. 디버그 레벨에서만 허용한다.
- AI 제공자 API key는 `.env`에 정의하고 Git에 커밋하지 않는다.

## 패키지 위치

```
src/main/java/travel_agency/pick_trip/infra/ai/
└── AiItineraryClient.java   ← AI 제공자 호출 인터페이스
```

도메인 코드(`itinerary` 패키지)는 `AiItineraryClient` 인터페이스에만 의존한다.
실제 구현체(프로바이더별)는 `infra/ai` 패키지에 위치시켜 교체 가능하게 유지한다.
