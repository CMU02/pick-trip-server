package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeCodeStore;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthExchangeData;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;
import travel_agency.pick_trip.domain.auth.repository.RefreshTokenRepository;
import travel_agency.pick_trip.domain.user.entity.User;
import travel_agency.pick_trip.gloal.jwt.JwtUserInfo;
import travel_agency.pick_trip.gloal.jwt.JwtUtil;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 소셜 로그인 성공 시 토큰을 URL 쿼리로 노출하지 않고, 일회용 opaque 교환 코드만 프론트로 전달한다.
 *
 * <p>실제 토큰은 서버 측 저장소({@link OAuthExchangeCodeStore})에 담기고, 프론트가
 * {@code POST /api/v1/auth/oauth/exchange} 로 code 를 제출해야 회수된다. 이렇게 하면
 * refreshToken 이 콜백 URL·브라우저 히스토리·리퍼러에 남지 않는다.</p>
 *
 * <p>또한 콜백의 {@code state} 로 개시 브라우저 nonce 를 회수해 교환 코드에 바인딩한다.
 * "이 콜백이 이 브라우저가 시작한 그 로그인의 결과"임을 교환 시점에 검증하기 위함이다.</p>
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthExchangeCodeStore exchangeCodeStore;
    private final OAuthNonceStore nonceStore;

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

        // 콜백의 state 로 개시 브라우저 nonce 를 회수해 교환 코드에 바인딩한다(nonce 미전달 시 null).
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        String nonce = (state != null) ? nonceStore.consume(state) : null;

        // 토큰을 서버 측 저장소에 담고 URL 에는 opaque code 만 실어 노출을 차단한다.
        String code = exchangeCodeStore.issue(
                new OAuthExchangeData(user.getUid(), accessToken, newRefreshToken, nonce));

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
