# API 엔드포인트 초안

이 문서는 PickTrip 서버가 클라이언트에 제공하는 내부 REST API 목록을 정의한다.
실제 요청/응답 스키마는 구현 후 OpenAPI(Swagger)로 확정한다.

## 공통 규칙

- 기본 접두사: `/api/v1`
- 인증이 필요한 API: `Authorization: Bearer <accessToken>` 헤더 필요
- 비로그인 허용 API: 인증 헤더 없이 호출 가능

## Auth

| 메서드  | URL                          | 인증 필요 | 설명                         |
| ------ | ---------------------------- | :------: | ---------------------------- |
| POST   | `/api/v1/auth/login/kakao`   | X        | 카카오 OAuth 로그인           |
| POST   | `/api/v1/auth/login/google`  | X        | 구글 OAuth 로그인             |
| POST   | `/api/v1/auth/token/refresh` | X        | 액세스 토큰 재발급            |
| DELETE | `/api/v1/auth/logout`        | O        | 로그아웃 (토큰 폐기)          |
| GET    | `/api/v1/auth/me`            | O        | 현재 로그인 사용자 정보 조회  |

## 콘텐츠

| 메서드 | URL                      | 인증 필요 | 설명                                      |
| ----- | ------------------------ | :------: | ----------------------------------------- |
| GET   | `/api/v1/contents`       | X        | 콘텐츠 목록 조회 (지역, 카테고리, 필터 등) |
| GET   | `/api/v1/contents/{id}`  | X        | 콘텐츠 상세 조회                           |

## 여행 바구니

| 메서드  | URL                               | 인증 필요 | 설명                     |
| ------ | --------------------------------- | :------: | ------------------------ |
| GET    | `/api/v1/baskets`                 | O        | 여행 바구니 조회          |
| POST   | `/api/v1/baskets/items`           | O        | 바구니에 콘텐츠 추가      |
| PATCH  | `/api/v1/baskets/items/{itemId}`  | O        | 바구니 항목 우선순위 변경 |
| DELETE | `/api/v1/baskets/items/{itemId}`  | O        | 바구니에서 콘텐츠 제거    |

## 일정

| 메서드 | URL                                       | 인증 필요 | 설명                     |
| ----- | ----------------------------------------- | :------: | ------------------------ |
| POST  | `/api/v1/itineraries/generate`            | O        | AI 일정 생성 요청         |
| POST  | `/api/v1/itineraries`                     | O        | 일정 저장                 |
| GET   | `/api/v1/itineraries/{id}`                | O        | 저장된 일정 상세 조회     |
| PATCH | `/api/v1/itineraries/{id}`                | O        | 일정 수정 (순서, 추가 등) |
| POST  | `/api/v1/itineraries/{id}/regenerate`     | O        | 전체 또는 하루 일정 재생성|

## 일정 공유

| 메서드 | URL                              | 인증 필요 | 설명                         |
| ----- | -------------------------------- | :------: | ---------------------------- |
| POST  | `/api/v1/itineraries/{id}/share` | O        | 공유 링크(토큰) 생성          |
| GET   | `/api/v1/share/{token}`          | X        | 공유 토큰으로 일정 조회       |
