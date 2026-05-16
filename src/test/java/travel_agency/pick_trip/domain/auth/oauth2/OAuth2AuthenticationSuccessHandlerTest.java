package travel_agency.pick_trip.domain.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2AuthenticationSuccessHandler")
class OAuth2AuthenticationSuccessHandlerTest {

    @InjectMocks
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final String REDIRECT_URI = "http://localhost:3000/auth/callback";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN_VALUE = "refresh-token";
    private static final long REFRESH_EXPIRE_DAYS = 14L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(successHandler, "redirectUri", REDIRECT_URI);
        ReflectionTestUtils.setField(successHandler, "refreshTokenExpireTimeDays", REFRESH_EXPIRE_DAYS);
    }

    private User userWithUid(UUID uid) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("google-sub")
                .email("test@gmail.com")
                .nickname("테스트")
                .profileImageUrl(null)
                .build();
        ReflectionTestUtils.setField(user, "uid", uid);
        return user;
    }

    private Authentication mockAuthentication(User user) {
        OAuth2UserAdapter adapter = mock(OAuth2UserAdapter.class);
        given(adapter.getUser()).willReturn(user);
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(adapter);
        return authentication;
    }

    @Nested
    @DisplayName("리다이렉트 URL")
    class RedirectUrl {

        @Test
        @DisplayName("인증 성공 시 accessToken과 refreshToken이 리다이렉트 URL 쿼리 파라미터에 포함된다")
        void onSuccess_redirectUrlContainsBothTokens() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            given(jwtUtil.generateAccessToken(any(JwtUserInfo.class))).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any(JwtUserInfo.class))).willReturn(REFRESH_TOKEN_VALUE);
            given(refreshTokenRepository.findById(USER_UID)).willReturn(Optional.empty());

            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), response, mockAuthentication(user));

            // then
            String redirectedUrl = response.getRedirectedUrl();
            assertThat(redirectedUrl).startsWith(REDIRECT_URI);
            assertThat(redirectedUrl).contains("accessToken=" + ACCESS_TOKEN);
            assertThat(redirectedUrl).contains("refreshToken=" + REFRESH_TOKEN_VALUE);
        }

        @Test
        @DisplayName("리다이렉트 URL의 기본 경로가 설정된 redirectUri와 일치한다")
        void onSuccess_redirectUrlBaseMatchesConfig() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN_VALUE);
            given(refreshTokenRepository.findById(USER_UID)).willReturn(Optional.empty());

            MockHttpServletResponse response = new MockHttpServletResponse();

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), response, mockAuthentication(user));

            // then
            assertThat(response.getRedirectedUrl()).startsWith(REDIRECT_URI + "?");
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 처리")
    class RefreshTokenHandling {

        @Test
        @DisplayName("최초 로그인 시 리프레시 토큰을 새로 저장한다")
        void firstLogin_savesNewRefreshToken() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN_VALUE);
            given(refreshTokenRepository.findById(USER_UID)).willReturn(Optional.empty());

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(user));

            // then
            then(refreshTokenRepository).should().save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("재로그인 시 기존 리프레시 토큰을 Rotate하고 새로 저장하지 않는다")
        void reLogin_rotatesExistingTokenWithoutSaving() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            RefreshToken existingToken = RefreshToken.of(USER_UID, "old-token",
                    LocalDateTime.now().plusDays(14));
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN_VALUE);
            given(refreshTokenRepository.findById(USER_UID)).willReturn(Optional.of(existingToken));

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(user));

            // then
            assertThat(existingToken.getToken()).isEqualTo(REFRESH_TOKEN_VALUE);
            then(refreshTokenRepository).should(never()).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("JWT 발급 검증")
    class JwtIssuing {

        @Test
        @DisplayName("JwtUtil에 DB 사용자 정보가 올바르게 전달된다")
        void onSuccess_passesCorrectUserInfoToJwtUtil() throws Exception {
            // given
            User user = userWithUid(USER_UID);
            given(jwtUtil.generateAccessToken(any())).willReturn(ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(REFRESH_TOKEN_VALUE);
            given(refreshTokenRepository.findById(USER_UID)).willReturn(Optional.empty());

            // when
            successHandler.onAuthenticationSuccess(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), mockAuthentication(user));

            // then
            JwtUserInfo expected = new JwtUserInfo(
                    USER_UID, user.getNickname(), user.getEmail(), user.getRole().name());
            then(jwtUtil).should().generateAccessToken(expected);
            then(jwtUtil).should().generateRefreshToken(expected);
        }
    }
}
