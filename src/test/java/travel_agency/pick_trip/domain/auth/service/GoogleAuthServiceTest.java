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
