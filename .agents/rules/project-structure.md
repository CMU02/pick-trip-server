# Project Structure

이 문서는 PickTrip Spring Boot 서버의 패키지와 디렉토리 구조 규칙을 정의한다. 코드 작성 방식은 `.agents/rules/code-convention.md`를 따른다.

## 기본 패키지

기본 패키지는 `travel_agency.pick_trip`을 사용한다.

```text
src/main/java/travel_agency/pick_trip/
├── PickTripApplication.java
├── global/
│   ├── config/
│   │   ├── WebConfig.java
│   │   └── ...
│   ├── error/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ErrorCode.java
│   │   ├── ErrorResponse.java
│   │   └── ...
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   └── jwt/
│   │       ├── JwtUtil.java
│   │       └── JwtUserPrincipal.java
│   ├── filter/
│   ├── scheduler/
│   └── util/
├── domain/
│   ├── auth/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── dto/
│   │   └── client/
│   │       ├── KakaoOAuthClient.java
│   │       └── GoogleOAuthClient.java
│   ├── user/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── region/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── content/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   └── mapper/
│   ├── basket/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── itinerary/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   └── ai/
│   ├── share/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   └── tourapi/
│       ├── client/
│       │   ├── TouristInformationClient.java
│       │   └── TouristPhotoGalleryClient.java
│       ├── dto/
│       └── service/
└── infra/
    ├── ai/
    │   └── AiItineraryClient.java
    └── storage/
```

## 최상위 패키지 책임

- `global`: 전역 설정, 공통 예외, 보안, 필터, 스케줄러, 유틸리티를 둔다.
- `domain`: PickTrip의 비즈니스 도메인을 둔다.
- `infra`: 특정 도메인에 직접 속하지 않는 외부 기술 연동을 둔다.

`global`은 도메인 로직을 가지지 않는다. `infra`는 외부 시스템 호출을 감싸되, PickTrip 유스케이스 판단은 각 도메인 `service`에서 수행한다.

## 도메인 패키지 기준

도메인은 기능 단위로 분리한다.

- `auth`: Kakao/Google OAuth, 로그인, 토큰 발급
- `user`: 사용자 계정과 프로필
- `region`: 하동, 영주, 예천 지역 코드와 지역 메타데이터
- `content`: 여행 콘텐츠 조회, 검색, 필터링
- `basket`: 여행 바구니와 콘텐츠 우선순위
- `itinerary`: AI 일정 생성, 수정, 저장
- `share`: 일정 공유 링크와 공유 조회
- `tourapi`: 한국관광공사 OpenAPI 수집, 정규화, 동기화

새 도메인은 독립적인 유스케이스와 저장 모델이 있을 때만 추가한다. 단순 공통 함수나 작은 enum을 새 도메인으로 분리하지 않는다.

## 도메인 하위 패키지 기준

- `controller`: HTTP API 엔드포인트
- `service`: 유스케이스, 트랜잭션 경계, 도메인 조합
- `repository`: JPA repository와 영속성 조회
- `entity`: JPA entity
- `dto`: API request/response, 외부 API DTO
- `client`: 외부 HTTP API 클라이언트
- `mapper`: entity, DTO, 외부 API 응답 변환

모든 도메인이 위 하위 패키지를 반드시 가져야 하는 것은 아니다. 실제 클래스가 생길 때 패키지를 만든다.

## TourAPI 위치

한국관광공사 API 연동은 `domain/tourapi`에 둔다.

- `client`: `KorService2`, `PhotoGalleryService1` 호출
- `dto`: TourAPI 요청/응답 DTO
- `service`: TourAPI 응답을 PickTrip 내부 모델로 정규화

TourAPI는 PickTrip 콘텐츠 도메인의 주요 데이터 원천이지만, 외부 API 호출 규칙과 응답 형태가 크기 때문에 `content` 안에 섞지 않는다. `content`는 정규화된 내부 콘텐츠 조회와 사용자-facing API에 집중한다.

## AI 연동 위치

AI 제공자 호출 자체는 `infra/ai`에 둔다.

일정 생성 유스케이스와 프롬프트 입력 구성은 `domain/itinerary`에서 담당한다. `infra/ai`는 HTTP 호출, 인증, 타임아웃, 응답 수신 같은 기술 연동만 담당한다.

## 테스트 구조

테스트 패키지는 main 패키지 구조를 따른다.

```text
src/test/java/travel_agency/pick_trip/
├── global/
│   └── error/
└── domain/
    ├── auth/
    ├── content/
    ├── itinerary/
    └── tourapi/
```

도메인 테스트는 해당 도메인 아래에 둔다. 공통 예외, 보안, 설정 테스트는 `global` 아래에 둔다.
