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
import travel_agency.pick_trip.domain.auth.client.KakaoApiClient;
import travel_agency.pick_trip.domain.auth.client.KakaoAuthClient;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoTokenResponse;
import travel_agency.pick_trip.domain.auth.dto.response.KakaoUserInfoResponse;
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
@DisplayName("KakaoAuthService")
class KakaoAuthServiceTest {

    @InjectMocks
    private KakaoAuthService kakaoAuthService;

    @Mock private KakaoAuthClient kakaoAuthClient;
    @Mock private KakaoApiClient kakaoApiClient;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;

    private static final String AUTH_CODE       = "test-auth-code";
    private static final String KAKAO_ACCESS    = "kakao-access-token";
    private static final String PROVIDER_ID     = "12345678";
    private static final String EMAIL           = "test@kakao.com";
    private static final String NICKNAME        = "테스트유저";
    private static final String PROFILE_IMG     = "https://img.kakao.com/profile.jpg";
    private static final String ACCESS_TOKEN    = "jwt-access-token";
    private static final String REFRESH_TOKEN   = "jwt-refresh-token";
    private static final String CLIENT_ID       = "test-client-id";
    private static final String REDIRECT_URI    = "http://localhost:3000/auth/callback";
    private static final long   REFRESH_DAYS    = 14L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kakaoAuthService, "clientId",                CLIENT_ID);
        ReflectionTestUtils.setField(kakaoAuthService, "redirectUri",             REDIRECT_URI);
        ReflectionTestUtils.setField(kakaoAuthService, "refreshTokenExpireTimeDays", REFRESH_DAYS);
    }

    // ─── 픽스처 ────────────────────────────────────────────────────────────────

    private KakaoTokenResponse kakaoTokenResponse() {
        return new KakaoTokenResponse(KAKAO_ACCESS, "kakao-refresh", 21600L, 5184000L);
    }

    private KakaoUserInfoResponse kakaoUserInfoResponse() {
        KakaoUserInfoResponse.KakaoAccount.Profile profile =
                new KakaoUserInfoResponse.KakaoAccount.Profile(NICKNAME, PROFILE_IMG);
        KakaoUserInfoResponse.KakaoAccount account =
                new KakaoUserInfoResponse.KakaoAccount(EMAIL, profile);
        return new KakaoUserInfoResponse(Long.parseLong(PROVIDER_ID), account);
    }

    /** uid를 강제 주입한 User 객체 (JPA 자동 생성 우회) */
    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(PROVIDER_ID)
                .email(EMAIL)
                .nickname(NICKNAME)
                .profileImageUrl(PROFILE_IMG)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        ReflectionTestUtils.setField(user, "role", Role.USER);
        return user;
    }

    private FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.POST, "https://kauth.kakao.com/oauth/token",
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
        @DisplayName("신규 유저가 카카오 로그인하면 계정이 생성되고 JWT가 반환된다")
        void firstLogin_createsUserAndReturnsJwt() {
            UUID uid = UUID.randomUUID();
            User newUser = userWithUid(uid);

            given(kakaoAuthClient.getToken(eq("authorization_code"), eq(CLIENT_ID),
                    eq(REDIRECT_URI), eq(AUTH_CODE)))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo("Bearer " + KAKAO_ACCESS))
                    .willReturn(kakaoUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(newUser);
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any(JwtUserInfo.class))).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any(JwtUserInfo.class))).willReturn(REFRESH_TOKEN);

            LoginResponse response = kakaoAuthService.login(AUTH_CODE);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            then(userRepository).should(times(1)).save(any(User.class));
            then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("카카오 이메일·프로필이 null이어도 로그인에 성공한다")
        void firstLogin_withNullEmailAndProfile_succeeds() {
            UUID uid = UUID.randomUUID();
            // 이메일·프로필 미동의 응답
            KakaoUserInfoResponse noInfoResponse =
                    new KakaoUserInfoResponse(Long.parseLong(PROVIDER_ID), null);
            User noInfoUser = userWithUid(uid);

            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo(anyString())).willReturn(noInfoResponse);
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(noInfoUser);
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            LoginResponse response = kakaoAuthService.login(AUTH_CODE);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
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

            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo(anyString())).willReturn(kakaoUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.of(storedToken));
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            LoginResponse response = kakaoAuthService.login(AUTH_CODE);

            assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
            // 재로그인이므로 save(User)는 호출되지 않는다
            then(userRepository).should(never()).save(any(User.class));
            // 리프레시 토큰은 새로 저장하지 않고 rotate만 한다
            then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
            // rotate 후 토큰 값이 바뀌었는지 확인
            assertThat(storedToken.getToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("재로그인 시 기존 리프레시 토큰이 없으면 새로 저장한다")
        void reLogin_noStoredRefreshToken_savesNew() {
            UUID uid = UUID.randomUUID();
            User existingUser = userWithUid(uid);

            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo(anyString())).willReturn(kakaoUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, PROVIDER_ID))
                    .willReturn(Optional.of(existingUser));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            kakaoAuthService.login(AUTH_CODE);

            then(refreshTokenRepository).should(times(1)).save(any(RefreshToken.class));
        }
    }

    // ─── 예외 시나리오 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("카카오 API 실패")
    class KakaoApiFailure {

        @Test
        @DisplayName("토큰 발급 API가 실패하면 OAuthProviderException(AUTH_PROVIDER_ERROR)을 던진다")
        void tokenFetch_feignException_throwsOAuthProviderException() {
            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willThrow(feignException(400));

            assertThatThrownBy(() -> kakaoAuthService.login(AUTH_CODE))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(kakaoApiClient).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("사용자 정보 조회 API가 실패하면 OAuthProviderException(AUTH_PROVIDER_ERROR)을 던진다")
        void userInfoFetch_feignException_throwsOAuthProviderException() {
            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo(anyString()))
                    .willThrow(feignException(401));

            assertThatThrownBy(() -> kakaoAuthService.login(AUTH_CODE))
                    .isInstanceOf(OAuthProviderException.class)
                    .extracting(e -> ((OAuthProviderException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_PROVIDER_ERROR);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("카카오 서버 5xx 오류도 OAuthProviderException으로 변환된다")
        void tokenFetch_serverError_throwsOAuthProviderException() {
            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willThrow(feignException(503));

            assertThatThrownBy(() -> kakaoAuthService.login(AUTH_CODE))
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

            given(kakaoAuthClient.getToken(anyString(), anyString(), anyString(), anyString()))
                    .willReturn(kakaoTokenResponse());
            given(kakaoApiClient.getUserInfo(anyString())).willReturn(kakaoUserInfoResponse());
            given(userRepository.findByProviderAndProviderUserId(any(), anyString()))
                    .willReturn(Optional.of(user));
            given(refreshTokenRepository.findById(uid)).willReturn(Optional.empty());
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN);

            kakaoAuthService.login(AUTH_CODE);

            // uid, role을 포함한 올바른 JwtUserInfo가 전달됐는지 캡처 검증
            then(jwtUtil).should().generateAccessToken(
                    new JwtUserInfo(uid, NICKNAME, EMAIL, Role.USER.name())
            );
            then(jwtUtil).should().generateRefreshToken(
                    new JwtUserInfo(uid, NICKNAME, EMAIL, Role.USER.name())
            );
        }
    }
}
