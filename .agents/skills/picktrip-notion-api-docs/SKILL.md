---
name: picktrip-notion-api-docs
description: Use when frontend team members need updated API documentation, or when new controllers/DTOs are added to the PickTrip server. Automatically scans Controller and DTO source files, extracts endpoint/request/response/error information, and updates the Notion "PickTrip Server Docs" page in the CPK여행사 teamspace.
---

# PickTrip Notion API Docs 업데이트 스킬

## 목적

PickTrip 서버의 Controller 코드를 자동 분석하여 Notion **"PickTrip Server Docs"** 페이지를 최신 API 정보로 업데이트한다.
프론트엔드 팀이 별도 문서를 관리하지 않아도 항상 최신 API 스펙을 확인할 수 있도록 한다.

## Notion 페이지 정보

| 항목 | 값 |
|------|-----|
| 페이지 이름 | PickTrip Server Docs |
| 팀스페이스 | CPK여행사 |
| 페이지 ID | `363a53ed-2396-81ed-967a-df466c1fe697` |
| URL | https://www.notion.so/363a53ed239681ed967adf466c1fe697 |

---

## 실행 절차

### 1단계: Controller 파일 수집

Glob으로 모든 Controller 파일을 탐색한다.

```
패턴: src/main/java/**/controller/**/*.java
```

각 Controller 파일에서 다음을 추출한다.
- 클래스 레벨 `@RequestMapping` → Base path
- 메서드 레벨 `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` → HTTP 메서드 + 경로
- `@PathVariable` → Path parameter
- `@RequestParam` → Query parameter (required, defaultValue 포함)
- `@RequestBody` → Request DTO 클래스명
- 반환 타입 → Response DTO 클래스명
- `@AuthenticationPrincipal` 존재 여부 → 인증 필요 여부 판단

### 2단계: DTO 파일 수집

1단계에서 식별한 Request / Response DTO 클래스를 아래 경로에서 찾아 읽는다.

```
패턴: src/main/java/**/dto/**/*.java
```

각 DTO에서 다음을 추출한다.
- 필드 이름 + Java 타입
- `@NotBlank` / `@NotNull` → 필수 필드 표시
- `record` 타입인 경우 생성자 파라미터 기준으로 필드 목록 추출

Java 타입 → JSON 타입 변환 기준:

| Java 타입 | JSON 타입 |
|-----------|-----------|
| `String` | `string` |
| `int` / `Integer` | `int` |
| `long` / `Long` | `long` |
| `double` / `Double` | `double` |
| `boolean` / `Boolean` | `boolean` |
| `UUID` | `string (UUID)` |
| `LocalDateTime` | `string (ISO 8601)` |
| `List<T>` | `T[]` |
| 기타 DTO | 중첩 객체로 표현 |

### 3단계: ErrorCode 수집

`src/main/java/**/error/ErrorCode.java` 파일을 읽어 도메인별 에러 코드 목록을 추출한다.
- 에러 코드명
- HTTP 상태 코드
- 메시지 (한국어)

### 4단계: Notion 페이지 업데이트

`notion-update-page` 도구를 사용해 기존 페이지 내용을 아래 구조로 완전 교체한다.

> **주의:** 페이지 **제목(title)은 변경하지 않는다.** content만 업데이트한다.

#### 페이지 구성 순서

```
[callout] 마지막 업데이트 날짜 | 대상: 프론트엔드 팀

## 공통 정보
  - Base URL 표
  - 인증 방식
  - 공통 에러 응답 형식 (JSON 코드블록)

---

## 🔐 Auth API
  각 엔드포인트마다:
  ### HTTP메서드 `경로`
  설명 한 줄
  [callout 🔒] 인증 필요 여부 (인증 필요한 경우만)
  Request Body (있는 경우 JSON 코드블록)
  Response HTTP코드 (JSON 코드블록)
  관련 에러 코드 bullet list

---

## 👤 User API
  (동일 구조)

---

## 🗺️ Content API
  (동일 구조)
  Query Parameter는 <table> 형식으로 표시

---

## ⚠️ 에러 코드 전체 목록
  도메인별 <table> (코드 | HTTP | 설명)
```

#### Notion 마크다운 형식 준수 사항

- 표는 반드시 Notion `<table header-row="true" fit-page-width="true">` 형식 사용
- 인증 필요 API는 `<callout icon="🔒">인증 필요 — Authorization: Bearer {accessToken} 헤더를 포함해야 합니다.</callout>` 추가
- JSON 예시는 ` ```json ` 코드블록으로 작성
- Notion 마크다운 spec은 `notion://docs/enhanced-markdown-spec` 리소스에서 확인

---

## 판단 기준

| 상황 | 처리 방법 |
|------|----------|
| `@AuthenticationPrincipal` 파라미터가 있는 메서드 | 🔒 인증 필요 callout 추가 |
| `@RequestParam(required = false)` | 표에서 필수 열을 `—` 으로 표시 |
| `@RequestParam(defaultValue = "...")` | 허용값 / 기본값 열에 기본값 명시 |
| Response가 `ResponseEntity<Void>` 또는 반환 없음 | `Response 204 No Content` 로 표시 |
| DTO 클래스를 찾을 수 없는 경우 | `(스키마 미정의)` 로 표시하고 계속 진행 |

---

## 실행 예시

사용자가 "API 문서 업데이트해줘" 또는 "Notion에 API 올려줘" 라고 요청하면 이 스킬을 실행한다.

1. 모든 Controller 파일 수집 → DTO 파일 수집 → ErrorCode 수집
2. 각 API 정보를 위 형식에 맞게 포맷
3. Notion MCP의 `notion-update-page`로 페이지 ID `363a53ed-2396-81ed-967a-df466c1fe697` 업데이트
4. 업데이트 완료 후 Notion 페이지 URL 반환

---

## 금지 사항

- 내부 구현 클래스(`Service`, `Repository`, `Adapter`)의 내부 로직을 문서에 포함하지 않는다.
- 민감 정보(JWT Secret, DB 비밀번호, OAuth Client Secret)를 Notion에 올리지 않는다.
- 아직 구현되지 않은 MVP 항목(바구니, 일정, 공유 등)은 "🚧 미구현" 주석과 함께 생략한다.
