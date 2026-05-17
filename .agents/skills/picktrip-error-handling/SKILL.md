---
name: picktrip-error-handling
description: Use when adding new ErrorCode entries, creating domain exception classes, implementing GlobalExceptionHandler, or handling external API failures in the PickTrip Spring Boot project. Ensures consistent exception hierarchy and error response structure.
---

# PickTrip Error Handling Pattern

## Overview

모든 예외는 `ErrorCode` enum → `PickTripException` 계층 → `@RestControllerAdvice` 공통 처리 구조를 따른다. 클라이언트에는 항상 `code / message / traceId` 세 필드만 노출한다.

## 1. ErrorCode Enum

`global/error/ErrorCode.java`에 정의. 새 도메인 추가 시 여기에 먼저 에러 코드를 추가한다.

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

    // Validation / Common
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;
}
```

## 2. Exception 계층

```java
// 기본 예외 (모든 도메인 예외의 부모)
@Getter
public class PickTripException extends RuntimeException {
    private final ErrorCode errorCode;
    public PickTripException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

// 도메인별 예외 (선택적으로 정의)
public class ContentException extends PickTripException {
    public ContentException(ErrorCode errorCode) { super(errorCode); }
}
```

## 3. GlobalExceptionHandler 패턴

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PickTripException.class)
    public ResponseEntity<ErrorResponse> handlePickTripException(
            PickTripException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, request.getAttribute("traceId")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, request.getAttribute("traceId")));
    }
}
```

## 4. Error Response Format

```json
{
  "code": "CONTENT_NOT_FOUND",
  "message": "콘텐츠를 찾을 수 없습니다.",
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## HTTP 상태 코드 매핑

| 상황 | HTTP 상태 |
|------|-----------|
| 입력값 검증 실패 | 400 Bad Request |
| 인증 실패 / 토큰 만료 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 외부 API 타임아웃 | 408 Request Timeout |
| 외부 API 장애 (TourAPI, AI, OAuth) | 502 Bad Gateway |
| 서버 내부 오류 | 500 Internal Server Error |

## ErrorCode 네이밍 규칙

`도메인_에러유형` 형식. 새 도메인 추가 시 prefix를 일관되게 사용한다.

| Prefix | 대상 도메인 |
|--------|------------|
| `AUTH_` | 인증 / 인가 |
| `CONTENT_` | 콘텐츠 조회 |
| `ITINERARY_` | 일정 생성 / 저장 |
| `SHARE_` | 일정 공유 |
| `VALIDATION_` | 입력값 검증 |
| `INTERNAL_` | 공통 서버 오류 |

## 금지 사항

- `exception.getMessage()`를 에러 응답에 직접 포함하지 않는다.
- SQL 오류, 토큰 값, 외부 API 응답 원문을 클라이언트에 노출하지 않는다.
- 외부 API 장애와 내부 데이터 부재를 같은 에러 코드로 처리하지 않는다 (디버깅이 불가능해진다).
- Controller 레이어에 `@Transactional`을 추가하지 않는다.
