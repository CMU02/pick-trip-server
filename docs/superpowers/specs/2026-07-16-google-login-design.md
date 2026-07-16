# 구글 소셜 로그인(authorization code 교환) 설계 스펙

**날짜:** 2026-07-16
**브랜치:** feat/30
**범위:** POST /api/v1/auth/login/google 신규 엔드포인트, 기존 Spring Security 기본 OAuth2 로그인 플로우 제거

---

## 1. 배경 및 목적

기존 구글 로그인은 Spring Security 기본 OAuth2 클라이언트 플로우(`/oauth2/authorization/google` 시작 → 리다이렉트 기반)를 사용했다. 이 플로우는 로그인 성공 시 JWT를 프론트 콜백 URL의 쿼리스트링에 담아 전달하며, httpOnly 쿠키로 관리되지 않아 카카오 로그인 구현과 보안 수준이 일관되지 않는다.

카카오 로그인(`POST /api/v1/auth/login/kakao`)은 프론트가 OAuth authorization code만 백엔드로 전달하고, 백엔드가 카카오 토큰 엔드포인트와 교환해 로그인을 처리한 뒤 JSON 응답 바디로 `accessToken`/`refreshToken`을 반환하는 커스텀 플로우다. 구글 로그인도 동일한 패턴으로 통일한다.

---

## 2. 아키텍처 결정

### 흐름

카카오 구현(`KakaoAuthService` → `KakaoAuthClient` → `KakaoApiClient`)을 그대로 미러링한다.

```
AuthController
  └── GoogleAuthService       (토큰 교환 → 사용자 정보 조회 → User upsert → JWT 발급)
        ├── GoogleAuthClient  (OpenFeign, oauth2.googleapis.com/token)
        └── GoogleApiClient   (OpenFeign, www.googleapis.com/oauth2/v3/userinfo)
```

### 사용자 정보 조회 방식

카카오와 동일하게 **토큰 교환 후 별도 userinfo API를 Feign으로 호출**하는 2단계 방식을 사용한다 (id_token 디코딩 방식은 채택하지 않음 — 패턴 일관성 우선).

### 기존 플로우 제거

다음 파일을 완전히 삭제한다 (더 이상 사용하지 않는 코드는 남기지 않는다):

- `domain/auth/oauth2/CustomOAuth2UserService.java` (+ `CustomOAuth2UserServiceTest.java`)
- `domain/auth/oauth2/OAuth2AuthenticationSuccessHandler.java` (+ `OAuth2AuthenticationSuccessHandlerTest.java`)
- `domain/auth/oauth2/OAuth2AuthenticationFailureHandler.java`
- `domain/auth/oauth2/OAuth2UserAdapter.java`
- `domain/auth/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java`

`SecurityConfig`에서 `oauth2Login(...)` 설정 및 관련 필드(4개)를 제거한다. `POST /api/v1/auth/login/google`은 이미 `permitAll()`로 등록되어 있어 그대로 유지한다.

### 설정 변경

`application-dev.yaml`, `application-prod.yaml`에서:

- 삭제: `spring.security.oauth2.client.registration.google` 블록, `app.oauth2.redirect-uri` 블록 (유일한 사용처인 `OAuth2AuthenticationSuccessHandler` 삭제로 완전히 미사용 상태가 됨 → `OAUTH2_REDIRECT_URI` env var도 더 이상 필요 없음)
- 추가: 카카오와 동일한 패턴의 `google` 설정 블록

```yaml
google:
  client-id: ${GOOGLE_CLIENT_ID}
  client-secret: ${GOOGLE_CLIENT_SECRET}
  redirect-uri: ${GOOGLE_REDIRECT_URI}
```

`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`는 기존 env var를 재사용한다. `GOOGLE_REDIRECT_URI`는 신규 env var로, 로컬 `.env`에는 프론트 콜백 주소인 `http://localhost:3000/auth/google/callback`을 설정한다 (카카오의 `KAKAO_REDIRECT_URI`와 동일하게 기본값 없이 필수 주입).

---

## 3. API 스펙

### POST /api/v1/auth/login/google

```
인증 필요: X (비로그인 허용)
```

**Request Body**

```json
{
  "authorizationCode": "string"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|:---:|---|
| `authorizationCode` | `String` | ✓ (`@NotBlank`) | 프론트가 구글 OAuth 인가 과정에서 받은 authorization code |

**Response Body** — 카카오 로그인과 완전히 동일한 형태 (`LoginResponse` 재사용)

```json
{
  "accessToken": "string",
  "refreshToken": "string"
}
```

`accessToken`은 카카오와 동일하게 JSON 응답 바디에 직접 포함되며, 쿠키를 사용하지 않는다.

**에러**

새 에러 코드를 추가하지 않고 기존 `ErrorCode.AUTH_PROVIDER_ERROR`(401) + `OAuthProviderException`을 재사용한다. 구글 토큰 교환/사용자 정보 조회 중 `FeignException` 발생 시 원문 메시지는 로그에만 남기고 클라이언트에는 노출하지 않는다.

---

## 4. 컴포넌트 상세

### DTO

- `dto/request/GoogleLoginRequest`: `record(@NotBlank String authorizationCode)` — `KakaoLoginRequest`와 동일 구조
- `dto/response/GoogleTokenResponse`: 구글 토큰 엔드포인트 응답 매핑 (`access_token`, `expires_in` 등 snake_case → `@JsonProperty`)
- `dto/response/GoogleUserInfoResponse`: 구글 userinfo 엔드포인트 응답 매핑. `sub`, `email`, `name`, `picture` 필드는 flat 구조 (카카오처럼 nested 아님). 별도 헬퍼 메서드 없이 record 필드(`sub()`/`email()`/`name()`/`picture()`)를 그대로 사용

### Feign 클라이언트

- `client/GoogleAuthClient`: `@FeignClient(url="https://oauth2.googleapis.com")`, `POST /token` (`application/x-www-form-urlencoded`, 파라미터: `grant_type=authorization_code`, `client_id`, `client_secret`, `redirect_uri`, `code`)
- `client/GoogleApiClient`: `@FeignClient(url="https://www.googleapis.com")`, `GET /oauth2/v3/userinfo` (헤더 `Authorization: Bearer {accessToken}`)

### Service

`service/GoogleAuthService`는 `KakaoAuthService.login()`과 동일한 흐름을 따른다:

1. `authorizationCode`로 구글 토큰 교환 (`GoogleAuthClient`)
2. 발급받은 access token으로 사용자 정보 조회 (`GoogleApiClient`)
3. `provider=GOOGLE`, `providerUserId=sub` 기준으로 `User` upsert (기존 유저면 닉네임/프로필 이미지만 최신화, 이메일은 최초 가입 시만 저장)
4. `JwtUtil`로 access/refresh 토큰 발급
5. 기존 refresh token이 있으면 rotate, 없으면 신규 저장
6. `LoginResponse(accessToken, refreshToken)` 반환

`FeignException`은 catch하여 로그 남긴 뒤 `OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR)`으로 변환한다 (토큰 교환/사용자 정보 조회 각각).

### Controller

`AuthController`에 `googleAuthService` 필드를 추가하고 다음 핸들러를 추가한다:

```java
@PostMapping("/login/google")
public ResponseEntity<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
    return ResponseEntity.ok(googleAuthService.login(request.authorizationCode()));
}
```

---

## 5. 테스트 계획

- `GoogleAuthServiceTest`: `KakaoAuthServiceTest`와 동일한 패턴 (`@ExtendWith(MockitoExtension.class)`, `@InjectMocks` + `@Mock`)
  - 최초 로그인 시 계정 생성 및 JWT 반환
  - 재로그인 시 프로필 업데이트 및 refresh token rotate
  - 재로그인 시 기존 refresh token 없으면 신규 저장
  - 토큰 교환 API 실패 → `OAuthProviderException(AUTH_PROVIDER_ERROR)`
  - 사용자 정보 조회 API 실패 → `OAuthProviderException(AUTH_PROVIDER_ERROR)`
  - JWT 발급 시 올바른 사용자 정보 전달 검증
- `AuthControllerTest`: `POST /api/v1/auth/login/google` nested 클래스 추가 (정상 요청 200, `authorizationCode` 빈 문자열/null → 400)
- 기존 `CustomOAuth2UserServiceTest`, `OAuth2AuthenticationSuccessHandlerTest`는 대상 클래스와 함께 삭제

TDD 컨벤션에 따라 테스트를 먼저 작성한 뒤 구현한다.

---

## 6. 범위 밖 (Out of Scope)

- 구글 refresh token 저장/사용 (구글이 내려주는 refresh_token은 사용하지 않음 — 카카오와 동일하게 우리 서비스의 자체 JWT refresh token만 사용)
- id_token 서명 검증 로직 (userinfo 엔드포인트 직접 호출 방식을 채택했으므로 불필요)
- 프론트엔드 콜백 페이지 구현
