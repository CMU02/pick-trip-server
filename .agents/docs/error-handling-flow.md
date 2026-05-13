# 예외 처리 흐름

이 문서는 Spring Boot 서버의 예외 처리 기준을 정의한다. 클라이언트가 모바일 앱이더라도 서버는 일관된 HTTP 상태 코드와 에러 응답 본문을 제공해야 한다.

## 에러 코드 설계

에러 코드는 Enum으로 정의하고, 커스텀 예외 클래스가 해당 Enum을 참조하는 구조로 관리한다.

### Enum 구조 예시

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다. 잠시 후 다시 시도해주세요."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다. 다시 로그인해주세요."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증입니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // Content
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "콘텐츠를 찾을 수 없습니다."),
    CONTENT_INVALID_REGION(HttpStatus.BAD_REQUEST, "지원하지 않는 지역입니다."),
    CONTENT_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "콘텐츠를 불러오지 못했습니다. 다시 시도해주세요."),

    // Itinerary
    ITINERARY_INPUT_INSUFFICIENT(HttpStatus.BAD_REQUEST, "일정 생성에 필요한 조건이 부족합니다."),
    ITINERARY_GENERATION_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "일정 생성에 실패했습니다. 다시 시도해주세요."),
    ITINERARY_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "일정 생성에 실패했습니다. 콘텐츠를 추가하거나 다시 시도해주세요."),
    ITINERARY_INVALID_CONTENT(HttpStatus.BAD_REQUEST, "유효하지 않은 콘텐츠가 포함되어 있습니다."),
    ITINERARY_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "저장에 실패했습니다. 다시 시도해주세요."),

    // Share
    SHARE_ITINERARY_NOT_FOUND(HttpStatus.NOT_FOUND, "공유된 일정을 찾을 수 없습니다."),
    SHARE_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    SHARE_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "공유 링크 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // Validation
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),

    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
```

### 커스텀 예외 구조 예시

```java
@Getter
public class PickTripException extends RuntimeException {

    private final ErrorCode errorCode;

    public PickTripException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

도메인별로 `PickTripException`을 상속한 예외를 정의해도 된다.

```java
public class AuthException extends PickTripException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
```

`@RestControllerAdvice`에서 `PickTripException`을 공통으로 처리한다.

```java
@ExceptionHandler(PickTripException.class)
public ResponseEntity<ErrorResponse> handlePickTripException(PickTripException e, HttpServletRequest request) {
    ErrorCode code = e.getErrorCode();
    return ResponseEntity.status(code.getStatus())
            .body(ErrorResponse.of(code, request.getAttribute("traceId")));
}
```

## 공통 에러 응답

모든 API 예외는 다음 형태로 응답한다.

```json
{
  "code": "AUTH_LOGIN_FAILED",
  "message": "로그인에 실패했습니다. 잠시 후 다시 시도해주세요.",
  "traceId": "request-trace-id"
}
```

- `code`: 클라이언트 분기 처리를 위한 안정적인 에러 코드
- `message`: 사용자에게 표시 가능한 한국어 메시지
- `traceId`: 서버 로그 추적용 식별자

서버 내부 예외 메시지, SQL, 토큰, 외부 API 원문 응답은 클라이언트에 노출하지 않는다.

## 1. 로그인 / 인증 실패

- 원인: Kakao 또는 Google OAuth 실패, 인가 코드 만료, 토큰 검증 실패, 네트워크 오류
- HTTP 상태: `401 Unauthorized`
- 에러 코드: `AUTH_LOGIN_FAILED`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`
- 처리: 인증 실패 응답을 반환하고 서버 로그에는 외부 제공자, 실패 단계, traceId를 남긴다.
- 메시지: "로그인에 실패했습니다. 잠시 후 다시 시도해주세요."

## 2. 권한 부족

- 원인: 다른 사용자의 일정 조회, 비공개 공유 링크 접근, 관리자 기능 접근
- HTTP 상태: `403 Forbidden`
- 에러 코드: `AUTH_FORBIDDEN`
- 처리: 리소스 존재 여부가 노출되지 않도록 상세 사유를 응답하지 않는다.
- 메시지: "접근 권한이 없습니다."

## 3. 콘텐츠 조회 실패

- 원인: 존재하지 않는 지역 코드, 콘텐츠 ID 없음, TourAPI 응답 오류, DB 조회 오류
- HTTP 상태: `400 Bad Request`, `404 Not Found`, `502 Bad Gateway`, `500 Internal Server Error`
- 에러 코드: `CONTENT_INVALID_REGION`, `CONTENT_NOT_FOUND`, `CONTENT_PROVIDER_FAILED`
- 처리: 외부 API 장애와 내부 데이터 부재를 구분해 로깅한다.
- 메시지: "콘텐츠를 불러오지 못했습니다. 다시 시도해주세요."

## 4. 입력값 검증 실패

- 원인: 여행 날짜 누락, 종료일이 시작일보다 빠름, 지원하지 않는 지역, 콘텐츠 선택 부족
- HTTP 상태: `400 Bad Request`
- 에러 코드: `VALIDATION_FAILED`
- 처리: 필드 단위 오류를 서버 로그에 남기고, 클라이언트에는 사용자 행동을 유도하는 메시지를 반환한다.
- 메시지: "입력값을 확인해주세요."

## 5. AI 일정 생성 실패

- 원인: AI 서버 오류, 타임아웃, 입력 데이터 부족, 운영시간 충돌
- HTTP 상태: `400 Bad Request`, `408 Request Timeout`, `502 Bad Gateway`
- 에러 코드: `ITINERARY_INPUT_INSUFFICIENT`, `ITINERARY_GENERATION_TIMEOUT`, `ITINERARY_PROVIDER_FAILED`
- 처리:
  - 입력 부족은 즉시 검증 오류로 반환한다.
  - 외부 AI 호출 타임아웃은 재시도 가능 상태로 반환한다.
  - 생성 실패 원인은 서버 로그에 남기되 AI 프롬프트 전문은 민감정보 기준에 따라 마스킹한다.
- 메시지: "일정 생성에 실패했습니다. 콘텐츠를 추가하거나 다시 시도해주세요."

## 6. 일정 저장 실패

- 원인: 인증 만료, 존재하지 않는 콘텐츠 참조, DB 저장 실패
- HTTP 상태: `401 Unauthorized`, `400 Bad Request`, `500 Internal Server Error`
- 에러 코드: `AUTH_TOKEN_EXPIRED`, `ITINERARY_INVALID_CONTENT`, `ITINERARY_SAVE_FAILED`
- 처리: 인증 만료와 저장 실패를 구분해 반환한다.
- 메시지: "저장에 실패했습니다. 다시 시도해주세요."

## 7. 일정 공유 실패

- 원인: 공유 대상 일정 없음, 소유자 불일치, 공유 링크 생성 실패
- HTTP 상태: `404 Not Found`, `403 Forbidden`, `500 Internal Server Error`
- 에러 코드: `SHARE_ITINERARY_NOT_FOUND`, `SHARE_FORBIDDEN`, `SHARE_CREATE_FAILED`
- 처리: 공유 토큰은 예측 불가능한 값으로 생성하고, 충돌 시 서버 내부에서 재시도한다.
- 메시지: "공유 링크 생성에 실패했습니다. 잠시 후 다시 시도해주세요."

## 8. 공통 서버 장애

- 원인: 예상하지 못한 런타임 예외, DB 연결 실패, 설정 누락
- HTTP 상태: `500 Internal Server Error`
- 에러 코드: `INTERNAL_SERVER_ERROR`
- 처리: 사용자 메시지는 일반화하고, 상세 원인은 traceId와 함께 서버 로그에서 확인한다.
- 메시지: "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
