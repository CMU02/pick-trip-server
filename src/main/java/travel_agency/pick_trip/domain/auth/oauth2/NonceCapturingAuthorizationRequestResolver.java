package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;

/**
 * 로그인 시작 요청(<code>/oauth2/authorization/{provider}?nonce=...</code>)에서 프론트가 넘긴
 * nonce 를 포착해, 그 요청에서 생성된 OAuth {@code state} 에 묶어 {@link OAuthNonceStore} 에 저장한다.
 *
 * <p>표준 {@link DefaultOAuth2AuthorizationRequestResolver} 에 위임해 인가요청과 state 를 그대로
 * 생성하므로, state 기반 CSRF 보호는 손상 없이 유지된다(우선순위 2). nonce 는 소셜 프로바이더로
 * 전송되지 않고(additionalParameters 가 아님) 서버 측 저장소에만 남아, 콜백 URL 에 노출되지 않는다.</p>
 */
@Component
public class NonceCapturingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** 프론트가 로그인 시작 시 전달하는 개시 바인딩 nonce 파라미터명. */
    public static final String NONCE_PARAM = "nonce";

    // Spring Security 기본 인가요청 base URI. oauth2Login 기본값과 동일해야 한다.
    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final OAuthNonceStore nonceStore;

    public NonceCapturingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuthNonceStore nonceStore) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI);
        this.nonceStore = nonceStore;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return captureNonce(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return captureNonce(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest captureNonce(OAuth2AuthorizationRequest authorizationRequest,
                                                    HttpServletRequest request) {
        // 로그인 시작 경로가 아니면 delegate 가 null 을 반환한다. 그대로 통과시킨다.
        if (authorizationRequest == null) {
            return null;
        }
        String nonce = request.getParameter(NONCE_PARAM);
        if (nonce != null && !nonce.isBlank()) {
            nonceStore.store(authorizationRequest.getState(), nonce);
        }
        return authorizationRequest;
    }
}
