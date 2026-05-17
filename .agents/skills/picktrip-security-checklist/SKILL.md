---
name: picktrip-security-checklist
description: Use when implementing OAuth login, external API integration (TourAPI, AI, Feign client), writing log statements, handling tokens or secrets, or reviewing any code that touches authentication, user data, or environment variables in the PickTrip project.
---

# PickTrip Security Checklist

## Overview

PickTrip은 OAuth 토큰, 사용자 일정, 외부 API 데이터, AI 생성 결과를 함께 다룬다. 보안 규칙은 기능 구현 중에 함께 적용한다 — 나중으로 미루지 않는다.

## 1. 인증 / 인가

- OAuth 토큰은 반드시 **서버에서 검증**한다. 클라이언트 검증만으로는 부족하다.
- 내부 사용자 ID와 외부 제공자 사용자 ID를 **분리 저장**한다.
- 다른 사용자의 바구니, 일정, 공유 설정 접근 시 **소유자 검증**을 수행한다.
- 공유 링크 조회는 별도 권한 정책을 사용하고, **내부 일정 ID를 URL에 직접 노출하지 않는다**.

## 2. 토큰 / 세션

- 액세스 토큰 만료 시간은 짧게 유지한다.
- 로그아웃 시 서버가 **재사용 가능한 인증 상태를 폐기**할 수 있어야 한다.
- 토큰, 인가 코드, OAuth secret은 **로그에 남기지 않는다**.

## 3. 환경 변수 / 설정

- DB 계정, OAuth client secret, AI API key, TourAPI key는 **`.env` 파일**에서 주입한다.
- `.env` 파일은 **Git에 커밋하지 않는다**.
- `application-prod.yaml`에는 운영 기본값만 두고 secret은 외부 주입한다.

## 4. 외부 API (OpenFeign) 연동

```java
// TourAPI 인증키는 헤더가 아닌 쿼리 파라미터로 주입
// RequestInterceptor를 사용해 serviceKey를 자동 첨부
@Component
public class TourApiAuthInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.query("serviceKey", apiKey);
        template.query("MobileOS", "ETC");
        template.query("MobileApp", "PickTrip");
        template.query("_type", "json");
    }
}
```

- Feign 클라이언트는 `global/client` 패키지에 위치시킨다.
- **OpenFeign 타임아웃을 명시적으로 설정**한다. 미설정 시 장애가 서버 전체로 전파된다.
- 외부 API 장애는 `502 Bad Gateway`로 구분해 응답한다. (→ [[picktrip-error-handling]])
- **인증키, API 원문 응답, 쿼리 파라미터 전문은 로그에 남기지 않는다**.

## 5. 입력값 검증

- 모든 요청 DTO는 **Bean Validation** (`@Valid`)으로 검증한다.
- 지역 코드, 카테고리, 우선순위는 **enum으로 제한**한다 (String 그대로 받지 않는다).
- 날짜 범위는 서버에서 검증한다 (종료일 > 시작일).
- 외부 공유 토큰, 콘텐츠 ID, 일정 ID는 형식과 **소유권을 모두 확인**한다.

## 6. 로그 규칙

로그에 절대 포함하지 않는 항목:

| 항목 | 이유 |
|------|------|
| 개인정보 (이메일, 이름) | 사용자 식별 위험 |
| 토큰 / 인가 코드 / OAuth secret | 탈취 시 계정 탈취 |
| 외부 API key (TourAPI, AI) | 과금 악용 |
| AI 프롬프트 전문 | 민감 여행 정보 포함 가능 |
| SQL 쿼리 원문 | 스키마 노출 |

- 오류 로그에는 `traceId`와 외부 제공자 상태를 함께 기록한다.
- 사용자 식별이 필요한 경우 **내부 ID** 또는 **마스킹된 값**을 사용한다.

## 7. 금지 사항 요약

- `exception.getMessage()`를 에러 응답에 그대로 포함하지 않는다.
- `application-prod.yaml`에 `ddl-auto: create` 또는 `create-drop`을 설정하지 않는다.
- Entity에 `@Setter`, `@Data`를 추가하지 않는다.
- Controller에 `@Transactional`을 추가하지 않는다.
- `.env`, 토큰, 비밀번호가 포함된 파일을 Git에 커밋하지 않는다.
