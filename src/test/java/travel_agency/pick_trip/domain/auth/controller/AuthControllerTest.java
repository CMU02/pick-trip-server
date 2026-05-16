package travel_agency.pick_trip.domain.auth.controller;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import travel_agency.pick_trip.domain.auth.dto.response.LoginResponse;
import travel_agency.pick_trip.domain.auth.dto.response.TokenRefreshResponse;
import travel_agency.pick_trip.domain.auth.service.KakaoAuthService;
import travel_agency.pick_trip.domain.auth.service.TokenService;
import travel_agency.pick_trip.gloal.error.GlobalExceptionHandler;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock private KakaoAuthService kakaoAuthService;
    @Mock private TokenService tokenService;
    @InjectMocks private AuthController authController;

    private static final UUID USER_UID = UUID.randomUUID();
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login/kakao")
    class KakaoLogin {

        @Test
        @DisplayName("유효한 authorizationCode로 요청하면 200과 토큰을 반환한다")
        void validRequest_returns200WithTokens() throws Exception {
            // given
            given(kakaoAuthService.login(anyString()))
                    .willReturn(new LoginResponse(ACCESS_TOKEN, REFRESH_TOKEN));

            // when / then
            mockMvc.perform(post("/api/v1/auth/login/kakao")
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
            mockMvc.perform(post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("authorizationCode 필드가 null이면 400을 반환한다")
        void nullAuthorizationCode_returns400() throws Exception {
            // when / then
            mockMvc.perform(post("/api/v1/auth/login/kakao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorizationCode\": null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/token/refresh")
    class TokenRefresh {

        @Test
        @DisplayName("유효한 refreshToken으로 요청하면 200과 새 토큰을 반환한다")
        void validRequest_returns200WithNewTokens() throws Exception {
            // given
            given(tokenService.refresh(anyString()))
                    .willReturn(new TokenRefreshResponse(ACCESS_TOKEN, REFRESH_TOKEN));

            // when / then
            mockMvc.perform(post("/api/v1/auth/token/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"old-refresh-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("refreshToken이 빈 문자열이면 400을 반환한다")
        void blankRefreshToken_returns400() throws Exception {
            // when / then
            mockMvc.perform(post("/api/v1/auth/token/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("인증된 사용자가 로그아웃하면 204를 반환한다")
        void authenticatedUser_returns204() {
            // given
            JwtUserPrincipal principal = principal();

            // when: standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
            ResponseEntity<Void> response = authController.logout(principal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            then(tokenService).should().logout(USER_UID);
        }
    }
}
