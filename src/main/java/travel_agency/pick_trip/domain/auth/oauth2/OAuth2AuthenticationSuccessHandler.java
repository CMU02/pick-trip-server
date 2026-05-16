package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2UserAdapter adapter = (OAuth2UserAdapter) authentication.getPrincipal();
        User user = adapter.getUser();

        JwtUserInfo jwtUserInfo = new JwtUserInfo(
                user.getUid(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name()
        );

        String accessToken = jwtUtil.generateAccessToken(jwtUserInfo);
        String newRefreshToken = jwtUtil.generateRefreshToken(jwtUserInfo);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpireTimeDays);

        // 재로그인 시 기존 리프레시 토큰을 갱신하고, 최초 로그인 시 새로 저장한다.
        refreshTokenRepository.findById(user.getUid())
                .ifPresentOrElse(
                        existing -> existing.rotate(newRefreshToken, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.of(user.getUid(), newRefreshToken, expiresAt))
                );

        // JWT를 쿼리 파라미터로 전달해 프론트엔드가 수신 후 안전한 저장소에 보관하도록 한다.
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", newRefreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
