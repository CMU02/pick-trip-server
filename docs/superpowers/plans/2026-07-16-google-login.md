# 구글 소셜 로그인(authorization code 교환) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /api/v1/auth/login/google` 엔드포인트를 카카오 로그인과 동일한 패턴(프론트가 authorization code만 전달 → 백엔드가 토큰 교환·사용자 정보 조회 → JWT 발급)으로 추가하고, 기존 Spring Security 기본 OAuth2 로그인 플로우를 완전히 제거한다.

**Architecture:** `AuthController` → `GoogleAuthService` → `GoogleAuthClient`(OpenFeign, 토큰 교환) → `GoogleApiClient`(OpenFeign, 사용자 정보 조회). `KakaoAuthService`/`KakaoAuthClient`/`KakaoApiClient` 구조를 그대로 미러링한다.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring MVC, OpenFeign, JUnit 5 + Mockito

## Global Constraints

- `jakarta.*` 패키지만 사용 (`javax.*` 금지)
- Entity에 `@Setter`, `@Data` 금지
- Controller에서 Entity 직접 반환 금지, Controller에 `@Transactional` 금지
- 에러 응답은 `{code, message, traceId}` 고정 포맷 — 새 에러 코드 추가 없이 기존 `ErrorCode.AUTH_PROVIDER_ERROR` 재사용
- 외부 API(GoogleAuth/GoogleApi) 호출은 반드시 `FeignException`을 캐치해 `OAuthProviderException`으로 변환 (원문 메시지는 로그에만 남기고 클라이언트에 노출 금지)
- 커밋 메시지는 `.agents/rules/git-convention.md` 형식(`<type>(scope): 제목`, 한국어)을 따른다
- 참고 스펙: `docs/superpowers/specs/2026-07-16-google-login-design.md`

---

## Task 1: 구글 DTO 작성

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/dto/request/GoogleLoginRequest.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/dto/response/GoogleTokenResponse.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/dto/response/GoogleUserInfoResponse.java`

**Interfaces:**
- Produces: `GoogleLoginRequest(String authorizationCode)`, `GoogleTokenResponse(String accessToken, Long expiresIn, String tokenType)`, `GoogleUserInfoResponse(String sub, String email, String name, String picture)` — Task 2·3에서 그대로 사용

이 파일들은 순수 데이터 레코드이며 카카오 쪽(`KakaoLoginRequest`, `KakaoTokenResponse`, `KakaoUserInfoResponse`)도 별도 단위 테스트 없이 컴파일 확인만으로 검증되어 있다. 동일한 방식을 따른다.

- [ ] **Step 1: GoogleLoginRequest 작성**

```java
package travel_agency.pick_trip.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank String authorizationCode
) {}
```

- [ ] **Step 2: GoogleTokenResponse 작성**

```java
package travel_agency.pick_trip.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("token_type") String tokenType
) {}
```

- [ ] **Step 3: GoogleUserInfoResponse 작성**

구글 `userinfo` 엔드포인트는 카카오와 달리 nested 구조가 아니라 flat 구조로 응답하므로 (`sub`, `email`, `name`, `picture`) 별도 null-처리 헬퍼 없이 record 필드를 그대로 사용한다.

```java
package travel_agency.pick_trip.domain.auth.dto.response;

public record GoogleUserInfoResponse(
        String sub,
        String email,
        String name,
        String picture
) {}
```

- [ ] **Step 4: 컴파일 확인**

Run: `.\gradlew.bat compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/travel_agency/pick_trip/domain/auth/dto/request/GoogleLoginRequest.java src/main/java/travel_agency/pick_trip/domain/auth/dto/response/GoogleTokenResponse.java src/main/java/travel_agency/pick_trip/domain/auth/dto/response/GoogleUserInfoResponse.java
git commit -m "feat(auth): 구글 로그인 요청/응답 DTO 추가"
```

---

## Task 2: 구글 Feign 클라이언트 및 설정 추가

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/client/GoogleAuthClient.java`
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/client/GoogleApiClient.java`
- Modify: `src/main/resources/application-dev.yaml`
- Modify: `src/main/resources/application-prod.yaml`
- Modify: `.env` (커밋되지 않는 로컬 파일)

**Interfaces:**
- Consumes: `GoogleTokenResponse`, `GoogleUserInfoResponse` (Task 1)
- Produces: `GoogleAuthClient.getToken(grantType, clientId, clientSecret, redirectUri, code): GoogleTokenResponse`, `GoogleApiClient.getUserInfo(bearerToken): GoogleUserInfoResponse` — Task 3에서 그대로 사용. 설정 프로퍼티 `google.client-id`, `google.client-secret`, `google.redirect-uri` — Task 3의 `GoogleAuthService`가 `@Value`로 주입받는다.

`PickTripApplication`에 이미 `@EnableFeignClients`가 선언되어 있어 별도 설정 없이 자동 스캔된다. `@SpringBootTest`(Testcontainers 기반 `PickTripApplicationTests`)가 전체 컨텍스트를 로드하므로, `GoogleAuthService`가 생성되기 전(Task 3)까지는 문제없지만 안전하게 이 태스크에서 yaml 설정을 먼저 추가해둔다.

- [ ] **Step 1: GoogleAuthClient 작성**

```java
package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleTokenResponse;

@FeignClient(name = "google-auth", url = "https://oauth2.googleapis.com")
public interface GoogleAuthClient {

    // 구글 토큰 API는 카카오와 마찬가지로 application/x-www-form-urlencoded만 허용한다.
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    GoogleTokenResponse getToken(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("code") String code
    );
}
```

- [ ] **Step 2: GoogleApiClient 작성**

```java
package travel_agency.pick_trip.domain.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleUserInfoResponse;

@FeignClient(name = "google-api", url = "https://www.googleapis.com")
public interface GoogleApiClient {

    @GetMapping("/oauth2/v3/userinfo")
    GoogleUserInfoResponse getUserInfo(@RequestHeader("Authorization") String bearerToken);
}
```

- [ ] **Step 3: application-dev.yaml에 google 설정 추가**

`kakao:` 블록 바로 뒤에 삽입한다 (기존 `spring.security.oauth2.client.registration.google` 블록은 Task 5에서 제거하므로 지금은 그대로 둔다).

변경 전:
```yaml
kakao:
  client-id: ${KAKAO_CLIENT_ID}
  redirect-uri: ${KAKAO_REDIRECT_URI}

app:
```

변경 후:
```yaml
kakao:
  client-id: ${KAKAO_CLIENT_ID}
  redirect-uri: ${KAKAO_REDIRECT_URI}

google:
  client-id: ${GOOGLE_CLIENT_ID}
  client-secret: ${GOOGLE_CLIENT_SECRET}
  redirect-uri: ${GOOGLE_REDIRECT_URI}

app:
```

- [ ] **Step 4: application-prod.yaml에 google 설정 추가**

변경 전:
```yaml
kakao:
  client-id: ${KAKAO_CLIENT_ID}
  redirect-uri: ${KAKAO_REDIRECT_URI}

app:
```

변경 후:
```yaml
kakao:
  client-id: ${KAKAO_CLIENT_ID}
  redirect-uri: ${KAKAO_REDIRECT_URI}

google:
  client-id: ${GOOGLE_CLIENT_ID}
  client-secret: ${GOOGLE_CLIENT_SECRET}
  redirect-uri: ${GOOGLE_REDIRECT_URI}

app:
```

- [ ] **Step 5: 로컬 .env에 GOOGLE_REDIRECT_URI 추가**

`.env`는 Git에 커밋되지 않는 로컬 파일이다 (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`는 이미 존재). `OAUTH2_REDIRECT_URI` 라인 근처에 다음을 추가한다.

```
GOOGLE_REDIRECT_URI=http://localhost:3000/auth/google/callback
```

- [ ] **Step 6: 컴파일 확인**

Run: `.\gradlew.bat compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

`.env`는 gitignore 대상이므로 커밋에서 제외한다.

```bash
git add src/main/java/travel_agency/pick_trip/domain/auth/client/GoogleAuthClient.java src/main/java/travel_agency/pick_trip/domain/auth/client/GoogleApiClient.java src/main/resources/application-dev.yaml src/main/resources/application-prod.yaml
git commit -m "feat(auth): 구글 토큰/사용자정보 Feign 클라이언트 및 설정 추가"
```

---

## Task 3: GoogleAuthService 구현 (TDD)

**Files:**
- Create: `src/main/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthService.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthServiceTest.java`

**Interfaces:**
- Consumes: `GoogleAuthClient.getToken(...)`, `GoogleApiClient.getUserInfo(...)` (Task 2), `UserRepository.findByProviderAndProviderUserId(OAuthProvider, String): Optional<User>`, `UserRepository.save(User): User`, `RefreshTokenRepository.findById(UUID): Optional<RefreshToken>`, `RefreshTokenRepository.save(RefreshToken): RefreshToken`, `JwtUtil.generateAccessToken(JwtUserInfo): String`, `JwtUtil.generateRefreshToken(JwtUserInfo): String`, `RefreshToken.of(UUID, String, LocalDateTime)`, `RefreshToken.rotate(String, LocalDateTime)`, `User.builder()...build()`, `User.updateProfile(String, String)`
- Produces: `GoogleAuthService.login(String authorizationCode): LoginResponse` — Task 4의 `AuthController`가 그대로 호출

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthServiceTest.java` 전체 내용:

```java
package travel_agency.pick_trip.domain.auth.service;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.auth.client.GoogleApiClient;
import travel_agency.pick_trip.domain.auth.client.GoogleAuthClient;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleTokenResponse;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleUserInfoResponse;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.Role;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleAuthService")
class GoogleAuthServiceTest {

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Mock private GoogleAuthClient googleAuthClient;
    @Mock private GoogleApiClient googleApiClient;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;

    private static final String AUTH_CODE       = "test-auth-code";
    private static final String GOOGLE_ACCESS   = "google-access-token";
    private static final String PROVIDER_ID     = "1080123456789012345";
    private static final String EMAIL           = "test@gmail.com";
    private static final String NAME            = "테스트유저";
    private static final String PICTURE         = "https://img.google.com/profile.jpg";
    private static final String ACCESS_TOKEN    = "jwt-access-token";
    private static final String REFRESH_TOKEN   = "jwt-refresh-token";
    private static final String CLIENT_ID       = "test-client-id";
    private static final String CLIENT_SECRET   = "test-client-secret";
    private static final String REDIRECT_URI    = "http://localhost:3000/auth/google/callback";
    private static final long   REFRESH_DAYS    = 14L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(googleAuthService, "clientId",                CLIENT_ID);
        ReflectionTestUtils.setField(googleAuthService, "clientSecret",            CLIENT_SECRET);
        ReflectionTestUtils.setField(googleAuthService, "redirectUri",             REDIRECT_URI);
        ReflectionTestUtils.setField(googleAuthService, "refreshTokenExpireTimeDays", REFRESH_DAYS);
    }

    // ─── 픽스처 ────────────────────────────────────────────────────────────────

    private GoogleTokenResponse googleTokenResponse() {
        return new GoogleTokenResponse(GOOGLE_ACCESS, 3600L, "Bearer");
    }

    private GoogleUserInfoResponse googleUserInfoResponse() {
        return new GoogleUserInfoResponse(PROVIDER_ID, EMAIL, NAME, PICTURE);
    }

    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId(PROVIDER_ID)
                .email(EMAIL)
                .nickname(NAME)
                .profileImageUrl(PICTURE)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        ReflectionTestUtils.setField(user, "role", Role.USER);
        return user;
    }

    private FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.POST, "https://oauth2.googleapis.com/token",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("getToken",
                feign.Response.builder()
                        .status(status)
                        .reason("error")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }

    // ─── 정상 시나리오 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("최초 로그인")
    class FirstLogin {

        @Test
        @DisplayName("신규 유저가 구글 로그인하면 계정이 생성되고 JWT가 반환된다")
        void firstLogin_createsUserAndReturnsJwt() {
            UUID uid = UUID.randomUUID();
            User newUser = userWithUid(uid);

            given(googleAuthClient.getToken(eq("authorization_code"), eq(CLIENT_ID),
                    eq(CLIENT_SECRET), eq(REDIRECT_URI), eq(AUTH_CODE)))
                    .willReturn(googleTokenResponse());
            given(googleApiClient.getUserInfo("Bearer " + GOOGLE_ACCESS))
                    .willReturn(googleUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(newUser);
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any(JwtUserInfo.class))).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any(JwtUserInfo.class))).willReturn(REFRESH_TOKEN);

            LoginResponse response = googleAuthService.login(AUTH_CODE);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            then(userRepository).should(times(1)).save(any(User.class));
            then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("재로그인")
    class ReLogin {

        @Test
        @DisplayName("기존 유저가 재로그인하면 프로필이 업데이트되고 리프레시 토큰이 Rotate된다")
        void reLogin_updatesProfileAndRotatesRefreshToken() {
            UUID uid = UUID.randomUUID();
            User existingUser = userWithUid(uid);
            RefreshToken storedToken = RefreshToken.of(uid, "old-refresh", LocalDateTime.now().plusDays(14));

            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(googleTokenResponse());
            given(googleApiClient.getUserInfo(anyString())).willReturn(googleUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.of(storedToken));
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            LoginResponse response = googleAuthService.login(AUTH_CODE);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            then(userRepository).should(never()).save(any(User.class));
            then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
            assertThat(storedToken.getToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("재로그인 시 기존 리프레시 토큰이 없으면 새로 저장한다")
        void reLogin_noStoredRefreshToken_savesNew() {
            UUID uid = UUID.randomUUID();
            User existingUser = userWithUid(uid);

            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(googleTokenResponse());
            given(googleApiClient.getUserInfo(anyString())).willReturn(googleUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            googleAuthService.login(AUTH_CODE);

            then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
        }
    }

    // ─── 예외 시나리오 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("구글 API 실패")
    class GoogleApiFailure {

        @Test
        @DisplayName("토큰 발급 API가 실패하면 OAuthProviderException(AUTH_PROVIDER_ERROR)을 던진다")
        void tokenFetch_feignException_throwsOAuthProviderException() {
            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willThrow(feignException(400));

            assertThatThrownBy(() -> googleAuthService.login(AUTH_CODE))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(googleApiClient).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("사용자 정보 조회 API가 실패하면 OAuthProviderException(AUTH_PROVIDER_ERROR)을 던진다")
        void userInfoFetch_feignException_throwsOAuthProviderException() {
            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(googleTokenResponse());
            given(googleApiClient.getUserInfo(anyString()))
                    .willThrow(feignException(401));

            assertThatThrownBy(() -> googleAuthService.login(AUTH_CODE))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("구글 서버 5xx 오류도 OAuthProviderException으로 변환된다")
        void tokenFetch_serverError_throwsOAuthProviderException() {
            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willThrow(feignException(503));

            assertThatThrownBy(() -> googleAuthService.login(AUTH_CODE))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);
        }
    }

    // ─── JWT 발급 검증 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JWT 발급")
    class JwtIssuing {

        @Test
        @DisplayName("로그인 성공 시 JwtUtil에 올바른 사용자 정보가 전달된다")
        void login_passesCorrectUserInfoToJwtUtil() {
            UUID uid = UUID.randomUUID();
            User user = userWithUid(uid);

            given(googleAuthClient.getToken(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .willReturn(googleTokenResponse());
            given(googleApiClient.getUserInfo(anyString())).willReturn(googleUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(any(), anyString()))
                    .willReturn(Optional.of(user));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            googleAuthService.login(AUTH_CODE);

            then(jwtUtil).should().generateAccessToken(
                    new JwtUserInfo(uid, NAME, EMAIL, Role.USER.name())
            );
            then(jwtUtil).should().generateRefreshToken(
                    new JwtUserInfo(uid, NAME, EMAIL, Role.USER.name())
            );
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.auth.service.GoogleAuthServiceTest"`
Expected: FAIL — `GoogleAuthService` 클래스가 존재하지 않아 컴파일 에러

- [ ] **Step 3: GoogleAuthService 구현**

`src/main/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthService.java` 전체 내용:

```java
package travel_agency.pick_trip.domain.auth.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.auth.client.GoogleApiClient;
import travel_agency.pick_trip.domain.auth.client.GoogleAuthClient;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleTokenResponse;
import travel_agency.pick_trip.domain.auth.dto.response.GoogleUserInfoResponse;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.OAuthProviderException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleAuthClient googleAuthClient;
    private final GoogleApiClient googleApiClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    @Transactional
    public LoginResponse login(String authorizationCode) {
        GoogleTokenResponse googleToken = fetchGoogleToken(authorizationCode);
        GoogleUserInfoResponse userInfo = fetchGoogleUserInfo(googleToken.accessToken());

        // 재로그인 시 닉네임·프로필 이미지를 최신 구글 정보로 동기화한다.
        // 이메일은 최초 가입 시만 저장하고 이후 변경하지 않는다.
        User user = userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, userInfo.sub())
                .map(existing -> {
                    existing.updateProfile(userInfo.name(), userInfo.picture());
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(OAuthProvider.GOOGLE)
                        .providerUserId(userInfo.sub())
                        .email(userInfo.email())
                        .nickname(userInfo.name())
                        .profileImageUrl(userInfo.picture())
                        .build()));

        JwtUserInfo jwtUserInfo = new JwtUserInfo(
                user.getUid(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name()
        );

        String newRefreshToken = jwtUtil.generateRefreshToken(jwtUserInfo);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpireTimeDays);

        // 재로그인 시 기존 리프레시 토큰을 갱신하고, 최초 로그인 시 새로 저장한다.
        refreshTokenRepository.findById(user.getUid())
                .ifPresentOrElse(
                        existing -> existing.rotate(newRefreshToken, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.of(user.getUid(), newRefreshToken, expiresAt))
                );

        return new LoginResponse(jwtUtil.generateAccessToken(jwtUserInfo), newRefreshToken);
    }

    private GoogleTokenResponse fetchGoogleToken(String authorizationCode) {
        try {
            return googleAuthClient.getToken("authorization_code", clientId, clientSecret, redirectUri, authorizationCode);
        } catch (FeignException e) {
            // 인가코드 만료, 이미 사용된 코드, 잘못된 redirect_uri 등이 원인일 수 있다.
            // 구글 원문 메시지는 보안상 클라이언트에 노출하지 않고 로그에만 남긴다.
            log.error("구글 토큰 발급 실패 - status: {}, message: {}", e.status(), e.getMessage());
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }
    }

    private GoogleUserInfoResponse fetchGoogleUserInfo(String accessToken) {
        try {
            return googleApiClient.getUserInfo("Bearer " + accessToken);
        } catch (FeignException e) {
            log.error("구글 사용자 정보 조회 실패 - status: {}, message: {}", e.status(), e.getMessage());
            throw new OAuthProviderException(ErrorCode.AUTH_PROVIDER_ERROR);
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.auth.service.GoogleAuthServiceTest"`
Expected: `BUILD SUCCESSFUL`, 7개 테스트 모두 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthService.java src/test/java/travel_agency/pick_trip/domain/auth/service/GoogleAuthServiceTest.java
git commit -m "feat(auth): 구글 authorization code 교환 로그인 서비스 구현"
```

---

## Task 4: AuthController 연동 (TDD)

**Files:**
- Modify: `src/main/java/travel_agency/pick_trip/domain/auth/controller/AuthController.java`
- Test: `src/test/java/travel_agency/pick_trip/domain/auth/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `GoogleAuthService.login(String): LoginResponse` (Task 3), `GoogleLoginRequest(String authorizationCode)` (Task 1)
- Produces: `POST /api/v1/auth/login/google` — `{"authorizationCode": string}` → 200 `{"accessToken": string, "refreshToken": string}`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/travel_agency/pick_trip/domain/auth/controller/AuthControllerTest.java`에서 두 곳을 수정한다.

import 및 mock 필드 추가 (변경 전):
```java
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;
import travel_agency.pick_trip.domain.auth.service.TokenService;
```

변경 후:
```java
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.GoogleAuthService;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;
import travel_agency.pick_trip.domain.auth.service.TokenService;
```

mock 필드 추가 (변경 전):
```java
    @Mock private KakaoAuthService kakaoAuthService;
    @Mock private TokenService tokenService;
    @InjectMocks private AuthController authController;
```

변경 후:
```java
    @Mock private KakaoAuthService kakaoAuthService;
    @Mock private GoogleAuthService googleAuthService;
    @Mock private TokenService tokenService;
    @InjectMocks private AuthController authController;
```

`KakaoLogin` nested 클래스 바로 뒤(그리고 `TokenRefresh` nested 클래스 앞)에 새 nested 클래스를 추가한다:

```java
    @Nested
    @DisplayName("POST /api/v1/auth/login/google")
    class GoogleLogin {

        @Test
        @DisplayName("유효한 authorizationCode로 요청하면 200과 토큰을 반환한다")
        void validRequest_returns200WithTokens() throws Exception {
            // given
            given(googleAuthService.login(anyString()))
                    .willReturn(new LoginResponse(ACCESS_TOKEN, REFRESH_TOKEN));

            // when / then
            mockMvc.perform(post("/api/v1/auth/login/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\": \"test-code\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("authorizationCode가 빈 문자열이면 400을 반환한다")
        void blankAuthorizationCode_returns400() throws Exception {
            // when / then
            mockMvc.perform(post("/api/v1/auth/login/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("authorizationCode 필드가 null이면 400을 반환한다")
        void nullAuthorizationCode_returns400() throws Exception {
            // when / then
            mockMvc.perform(post("/api/v1/auth/login/google")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\": null}"))
                    .andExpect(status().isBadRequest());
        }
    }
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.auth.controller.AuthControllerTest"`
Expected: FAIL — `AuthController`에 `googleLogin` 핸들러가 없어 컴파일 에러 또는 404

- [ ] **Step 3: AuthController 수정**

변경 전:
```java
import travel_agency.pick_trip.domain.auth.dto.request.KakaoLoginRequest;
import travel_agency.pick_trip.domain.auth.dto.request.TokenRefreshRequest;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;
import travel_agency.pick_trip.domain.auth.service.TokenService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final TokenService tokenService;

    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(kakaoAuthService.login(request.authorizationCode()));
    }
```

변경 후:
```java
import travel_agency.pick_trip.domain.auth.dto.request.GoogleLoginRequest;
import travel_agency.pick_trip.domain.auth.dto.request.KakaoLoginRequest;
import travel_agency.pick_trip.domain.auth.dto.request.TokenRefreshRequest;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.GoogleAuthService;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;
import travel_agency.pick_trip.domain.auth.service.TokenService;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KakaoAuthService kakaoAuthService;
    private final GoogleAuthService googleAuthService;
    private final TokenService tokenService;

    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(kakaoAuthService.login(request.authorizationCode()));
    }

    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(googleAuthService.login(request.authorizationCode()));
    }
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.auth.controller.AuthControllerTest"`
Expected: `BUILD SUCCESSFUL`, 전체 테스트 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/travel_agency/pick_trip/domain/auth/controller/AuthController.java src/test/java/travel_agency/pick_trip/domain/auth/controller/AuthControllerTest.java
git commit -m "feat(auth): POST /api/v1/auth/login/google 엔드포인트 추가"
```

---

## Task 5: 기존 Spring Security OAuth2 플로우 삭제

**Files:**
- Delete: `src/main/java/travel_agency/pick_trip/domain/auth/oauth2/CustomOAuth2UserService.java`
- Delete: `src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationSuccessHandler.java`
- Delete: `src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationFailureHandler.java`
- Delete: `src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2UserAdapter.java`
- Delete: `src/main/java/travel_agency/pick_trip/domain/auth/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java`
- Delete: `src/test/java/travel_agency/pick_trip/domain/auth/oauth2/CustomOAuth2UserServiceTest.java`
- Delete: `src/test/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationSuccessHandlerTest.java`
- Modify: `src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java`
- Modify: `src/main/resources/application-dev.yaml`
- Modify: `src/main/resources/application-prod.yaml`

**Interfaces:**
- 이 태스크는 어떤 새 인터페이스도 만들지 않는다. Task 1~4에서 만든 `POST /api/v1/auth/login/google`이 이제 유일한 구글 로그인 경로가 된다.

이 5개 소스 파일과 2개 테스트 파일은 `SecurityConfig.java` 외에는 어디서도 참조되지 않는다 (사전 확인 완료).

- [ ] **Step 1: oauth2 패키지 파일 삭제**

```bash
git rm src/main/java/travel_agency/pick_trip/domain/auth/oauth2/CustomOAuth2UserService.java
git rm src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationSuccessHandler.java
git rm src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationFailureHandler.java
git rm src/main/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2UserAdapter.java
git rm src/main/java/travel_agency/pick_trip/domain/auth/oauth2/HttpCookieOAuth2AuthorizationRequestRepository.java
git rm src/test/java/travel_agency/pick_trip/domain/auth/oauth2/CustomOAuth2UserServiceTest.java
git rm src/test/java/travel_agency/pick_trip/domain/auth/oauth2/OAuth2AuthenticationSuccessHandlerTest.java
```

- [ ] **Step 2: SecurityConfig에서 oauth2Login 설정 제거**

`src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java` 전체를 다음으로 교체한다:

```java
package travel_agency.pick_trip.gloal.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import travel_agency.pick_trip.gloal.filter.JwtFilter;
import travel_agency.pick_trip.gloal.filter.TraceIdFilter;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final TraceIdFilter traceIdFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth - 인증 불필요
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/kakao").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token/refresh").permitAll()
                        // Content - 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents/**").permitAll()
                        // Share - 공유 토큰 조회 인증 불필요
                        .requestMatchers(HttpMethod.GET, "/api/v1/share/**").permitAll()
                        // API 문서 / MCP 서버 - 인증 불필요 (read-only 문서 제공)
                        .requestMatchers("/sse", "/mcp/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Basket - 여행 바구니는 로그인 필요
                        .requestMatchers("/api/v1/baskets/**").authenticated()
                        // Itinerary - 일정 생성·저장·조회·수정·공유 생성은 로그인 필요
                        .requestMatchers("/api/v1/itineraries/**").authenticated()
                        // 나머지 모든 요청은 인증 필요
                        // TODO: 현재 개발 중이기 때문에 잠시 요청은 인증 불필요, 추후 authenticated() 설정
                        .anyRequest().permitAll()

                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint))
                .addFilterBefore(traceIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

- [ ] **Step 3: application-dev.yaml에서 미사용 설정 제거**

변경 전 (파일 최상단):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            clientId: ${GOOGLE_CLIENT_ID}
            clientSecret: ${GOOGLE_CLIENT_SECRET}

jwt:
```

변경 후:
```yaml
jwt:
```

변경 전 (`app:` 블록):
```yaml
app:
  cors:
    # LAN IP 접속 허용은 dev 프로필 전용 (prod의 application-prod.yaml에는 추가하지 않음)
    allowed-origins: http://localhost:3000,http://192.168.*.*:3000
  oauth2:
    redirect-uri: ${OAUTH2_REDIRECT_URI}

tour-api:
```

변경 후:
```yaml
app:
  cors:
    # LAN IP 접속 허용은 dev 프로필 전용 (prod의 application-prod.yaml에는 추가하지 않음)
    allowed-origins: http://localhost:3000,http://192.168.*.*:3000

tour-api:
```

- [ ] **Step 4: application-prod.yaml에서 미사용 설정 제거**

변경 전 (파일 최상단):
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_URL}:${DB_PORT}/${DB_SCHEMA}?serverTimezone=Asia/Seoul
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  security:
    oauth2:
      client:
        registration:
          google:
            clientId: ${GOOGLE_CLIENT_ID}
            clientSecret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile


public-data-portal:
```

변경 후:
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_URL}:${DB_PORT}/${DB_SCHEMA}?serverTimezone=Asia/Seoul
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

public-data-portal:
```

변경 전 (`app:` 블록):
```yaml
app:
  cors:
    allowed-origins: http://localhost:3000
  oauth2:
    redirect-uri: ${OAUTH2_REDIRECT_URI}

tour-api:
```

변경 후:
```yaml
app:
  cors:
    allowed-origins: http://localhost:3000

tour-api:
```

- [ ] **Step 5: 관련 테스트 전체 실행 → 통과 확인**

Run: `.\gradlew.bat test --tests "travel_agency.pick_trip.domain.auth.*"`
Expected: `BUILD SUCCESSFUL` — `KakaoAuthServiceTest`, `GoogleAuthServiceTest`, `AuthControllerTest` 모두 PASS (삭제된 `CustomOAuth2UserServiceTest`, `OAuth2AuthenticationSuccessHandlerTest`는 더 이상 실행되지 않음)

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/travel_agency/pick_trip/domain/auth/oauth2 src/test/java/travel_agency/pick_trip/domain/auth/oauth2 src/main/java/travel_agency/pick_trip/gloal/security/SecurityConfig.java src/main/resources/application-dev.yaml src/main/resources/application-prod.yaml
git commit -m "refactor(auth): 구글 커스텀 로그인 도입에 따라 기존 Spring Security OAuth2 플로우 제거"
```

---

## Task 6: 최종 검증

**Files:** 없음 (검증 전용)

- [ ] **Step 1: 전체 테스트 실행**

Run: `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`. `PickTripApplicationTests`(Testcontainers 기반 `contextLoads`)는 로컬 Docker가 필요하다 — Docker가 없어 이 테스트만 실패하면 사전에 존재하던 환경 제약이므로 무시하고, 그 외 전체 테스트(특히 `domain.auth.*`)가 통과하는지 확인한다.

- [ ] **Step 2: 전체 빌드 확인**

Run: `.\gradlew.bat build -x test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 변경 파일 최종 점검**

Run: `git status` 및 `git log --oneline main..HEAD`
Expected: Task 1~5의 커밋 5개만 존재하고, `.env`는 추적되지 않음을 확인
