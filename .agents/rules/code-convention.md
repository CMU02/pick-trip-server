# Code Convention

이 문서는 PickTrip Spring Boot 서버의 코드 작성 규칙을 정의한다.

## 기술 기준

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- Spring Security
- OpenFeign
- MySQL
- Gradle
- Lombok
- JUnit 5

## 패키지 구조

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
│   ├── filter
│   ├── scheduler
│   └── util
├── domain/
│   ├── auth/
│   │   ├── controller
│   │   ├── service
│   │   ├── dto
│   │   └── client/
│   │       ├── KakaoOAuthClient.java
│   │       └── GoogleOAuthClient.java
│   ├── user/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── region/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── content/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   ├── dto
│   │   └── mapper
│   ├── basket/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   ├── itinerary/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   ├── dto
│   │   └── ai
│   ├── share/
│   │   ├── controller
│   │   ├── service
│   │   ├── repository
│   │   ├── entity
│   │   └── dto
│   └── tourapi/
│       ├── client/
│       │   ├── TouristInformationClient.java
│       │   └── TouristPhotoGalleryClient.java
│       ├── dto
│       └── service
└── infra/
    ├── ai/
    │   └── AiItineraryClient.java
    └── storage
```

## 계층 규칙

- `controller`: HTTP 요청과 응답을 담당한다.
- `service`: 유스케이스와 트랜잭션 경계를 담당한다.
- `repository`: 영속성 접근을 담당한다.
- `entity`: DB 테이블과 매핑되는 도메인 상태를 표현한다.
- `dto`: API 요청과 응답 모델을 표현한다.
- `exception`: 도메인별 예외를 정의한다.

Controller는 Entity를 직접 반환하지 않는다. API 응답은 DTO로 변환한다.

## 네이밍 규칙

- 클래스: `PascalCase`
  - 예: `ContentController`, `ItineraryService`
- 메서드와 변수: `camelCase`
  - 예: `findContents`, `createItinerary`
- 상수: `UPPER_SNAKE_CASE`
  - 예: `DEFAULT_PAGE_SIZE`
- 패키지: 소문자와 단어 구분용 언더스코어 또는 단일 단어
  - 현재 기본 패키지 `travel_agency.pick_trip`을 유지한다.

## 클래스 접미사

- Controller: `*Controller`
- Service: `*Service`
- Repository: `*Repository`
- Entity: 도메인명 단수형
  - 예: `User`, `TravelContent`, `Itinerary`
- Request DTO: `*Request`
- Response DTO: `*Response`
- Config: `*Config`
- Exception: `*Exception`

## API 규칙

- REST API는 명사를 중심으로 설계한다.
- API 버전은 필요 시 `/api/v1` 접두사를 사용한다.
- 생성은 `POST`, 조회는 `GET`, 전체 수정은 `PUT`, 일부 수정은 `PATCH`, 삭제는 `DELETE`를 사용한다.
- 페이징 목록은 Spring `Pageable` 또는 명시적인 page/size 파라미터를 사용한다.
- 성공 응답과 에러 응답의 형태는 일관되게 유지한다.

## DTO 규칙

- 요청 DTO에는 Bean Validation annotation을 사용한다.
- 응답 DTO는 클라이언트에 필요한 값만 포함한다.
- Entity를 request body로 직접 받지 않는다.
- Entity를 response body로 직접 반환하지 않는다.
- 날짜와 시간은 명확한 타입을 사용한다.
  - 날짜: `LocalDate`
  - 날짜와 시간: `LocalDateTime`
  - 외부 API 시간대가 중요한 경우 `OffsetDateTime` 고려

## 트랜잭션 규칙

- 조회 전용 서비스 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 상태를 변경하는 서비스 메서드는 `@Transactional`을 사용한다.
- Controller에는 트랜잭션을 두지 않는다.
- 외부 API 호출과 DB 트랜잭션을 같은 범위에 오래 묶지 않는다.

## JPA 규칙

- Entity의 ID는 가능하면 `Long`을 사용한다.
- 연관관계는 필요한 방향으로만 선언한다.
- `FetchType.LAZY`를 기본으로 고려한다.
- 양방향 연관관계는 필요할 때만 사용한다.
- N+1 문제가 예상되는 조회는 fetch join, EntityGraph, 전용 조회 DTO를 검토한다.
- 운영 환경에서 `ddl-auto=create`를 사용하지 않는다.

## 예외 처리 규칙

- 도메인 예외는 명시적인 에러 코드를 가진다.
- `@RestControllerAdvice`에서 예외를 공통 응답으로 변환한다.
- 서버 내부 예외 메시지를 클라이언트에 그대로 노출하지 않는다.
- 인증, 권한, 검증, 외부 API 장애, 내부 서버 오류를 구분한다.

## Lombok 규칙

- Entity에는 `@Setter`를 기본으로 사용하지 않는다.
- Entity에는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- DTO에는 필요한 경우 `record`를 우선 고려한다.
- `@Data`는 사용하지 않는다.
- 순환 참조와 equals/hashCode 문제를 피하기 위해 Entity에 `@EqualsAndHashCode`를 신중히 사용한다.

## 테스트 규칙

→ `.agents/rules/test-convention.md` 참고

## TraceId 규칙

→ `.agents/rules/trace-id.md` 참고

## 설정 규칙

- 환경별 설정은 Spring profile로 분리한다.
- secret 값은 Git에 커밋하지 않는다.
- 운영 설정에는 안전한 기본값을 사용한다.
- 개발 편의를 위한 설정은 운영 프로필에 섞지 않는다.
