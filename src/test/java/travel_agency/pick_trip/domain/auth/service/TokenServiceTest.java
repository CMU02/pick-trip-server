package travel_agency.pick_trip.domain.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.OAuthProvider;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.AuthException;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService")
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final String STORED_REFRESH_TOKEN = "stored-refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";
    private static final long REFRESH_EXPIRE_DAYS = 14L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenService, "refreshTokenExpireTimeDays", REFRESH_EXPIRE_DAYS);
    }

    private User activeUser() {
        User user = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId("kakao-id")
                .email("test@kakao.com")
                .nickname("테스트")
                .profileImageUrl(null)
                .build();
        ReflectionTestUtils.setField(user, "uid", USER_UID);
        return user;
    }

    private RefreshToken storedToken() {
        return RefreshToken.of(USER_UID, STORED_REFRESH_TOKEN, LocalDateTime.now().plusDays(14));
    }

    @SuppressWarnings("unchecked")
    private Jws<Claims> mockJws() {
        return mock(Jws.class);
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Refresh {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 새 액세스 토큰과 리프레시 토큰이 반환된다")
        void refresh_validToken_returnsNewTokens() {
            // given
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willReturn(mockJws());
            given(refreshTokenRepository.findByToken(STORED_REFRESH_TOKEN)).willReturn(Optional.of(storedToken()));
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(activeUser()));
            given(jwtUtil.generateAccessToken(any(JwtUserInfo.class))).willReturn(NEW_ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any(JwtUserInfo.class))).willReturn(NEW_REFRESH_TOKEN);

            // when
            TokenRefreshResponse response = tokenService.refresh(STORED_REFRESH_TOKEN);

            // then
            assertThat(response.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
            assertThat(response.refreshToken()).isEqualTo(NEW_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("토큰 재발급 시 저장된 리프레시 토큰이 새 값으로 Rotate된다")
        void refresh_rotatesStoredRefreshToken() {
            // given
            RefreshToken stored = storedToken();
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willReturn(mockJws());
            given(refreshTokenRepository.findByToken(STORED_REFRESH_TOKEN)).willReturn(Optional.of(stored));
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(activeUser()));
            given(jwtUtil.generateAccessToken(any())).willReturn(NEW_ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(NEW_REFRESH_TOKEN);

            // when
            tokenService.refresh(STORED_REFRESH_TOKEN);

            // then: 기존 토큰 객체의 값이 새 값으로 변경됐는지 확인
            assertThat(stored.getToken()).isEqualTo(NEW_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("만료된 리프레시 토큰이면 AUTH_EXPIRED_TOKEN 예외를 던진다")
        void refresh_expiredToken_throwsExpiredTokenException() {
            // given
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willThrow(mock(ExpiredJwtException.class));

            // when / then
            assertThatThrownBy(() -> tokenService.refresh(STORED_REFRESH_TOKEN))
                    .isInstanceOf(AuthException.class)
                    .extracting(e -> ((AuthException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_EXPIRED_TOKEN);

            then(refreshTokenRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("위조된 리프레시 토큰이면 AUTH_INVALID_TOKEN 예외를 던진다")
        void refresh_tamperedToken_throwsInvalidTokenException() {
            // given
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willThrow(mock(JwtException.class));

            // when / then
            assertThatThrownBy(() -> tokenService.refresh(STORED_REFRESH_TOKEN))
                    .isInstanceOf(AuthException.class)
                    .extracting(e -> ((AuthException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_INVALID_TOKEN);

            then(refreshTokenRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("DB에 없는 리프레시 토큰이면 AUTH_REFRESH_TOKEN_NOT_FOUND 예외를 던진다")
        void refresh_tokenNotInDb_throwsTokenNotFoundException() {
            // given
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willReturn(mockJws());
            given(refreshTokenRepository.findByToken(STORED_REFRESH_TOKEN)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> tokenService.refresh(STORED_REFRESH_TOKEN))
                    .isInstanceOf(AuthException.class)
                    .extracting(e -> ((AuthException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("탈퇴한 사용자의 토큰 재발급은 AUTH_FORBIDDEN 예외를 던진다")
        void refresh_deletedUser_throwsForbiddenException() {
            // given
            User deletedUser = activeUser();
            deletedUser.withdraw();
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willReturn(mockJws());
            given(refreshTokenRepository.findByToken(STORED_REFRESH_TOKEN)).willReturn(Optional.of(storedToken()));
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(deletedUser));

            // when / then
            assertThatThrownBy(() -> tokenService.refresh(STORED_REFRESH_TOKEN))
                    .isInstanceOf(AuthException.class)
                    .extracting(e -> ((AuthException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
        }

        @Test
        @DisplayName("재발급 시 JwtUtil에 DB에서 조회한 최신 사용자 정보가 전달된다")
        void refresh_passesLatestUserInfoToJwtUtil() {
            // given
            User user = activeUser();
            given(jwtUtil.parseToken(STORED_REFRESH_TOKEN)).willReturn(mockJws());
            given(refreshTokenRepository.findByToken(STORED_REFRESH_TOKEN)).willReturn(Optional.of(storedToken()));
            given(userRepository.findById(USER_UID)).willReturn(Optional.of(user));
            given(jwtUtil.generateAccessToken(any())).willReturn(NEW_ACCESS_TOKEN);
            given(jwtUtil.generateRefreshToken(any())).willReturn(NEW_REFRESH_TOKEN);

            // when
            tokenService.refresh(STORED_REFRESH_TOKEN);

            // then
            JwtUserInfo expected = new JwtUserInfo(USER_UID, user.getNickname(), user.getEmail(), user.getRole().name());
            then(jwtUtil).should().generateAccessToken(expected);
            then(jwtUtil).should().generateRefreshToken(expected);
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 시 해당 사용자의 리프레시 토큰이 삭제된다")
        void logout_deletesRefreshTokenByUserId() {
            // when
            tokenService.logout(USER_UID);

            // then
            then(refreshTokenRepository).should().deleteById(USER_UID);
        }

        @Test
        @DisplayName("로그아웃 시 사용자 저장소에는 접근하지 않는다")
        void logout_doesNotAccessUserRepository() {
            // when
            tokenService.logout(USER_UID);

            // then
            then(userRepository).shouldHaveNoInteractions();
        }
    }
}
