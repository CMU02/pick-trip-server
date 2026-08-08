package travel_agency.pick_trip.domain.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;

/**
 * 로그인 시작 요청(<code>/oauth2/authorization/{provider}?nonce=...&amp;client=app</code>)에서 프론트가 넘긴
 * nonce 를 포착해, 그 요청에서 생성된 OAuth {@code state} 에 묶어 {@link OAuthNonceStore} 에 저장한다.
 * 또한 앱(모바일) 클라이언트가 시작한 로그인이면 {@code state} 에 표식을 남겨, 성공 핸들러가 웹 콜백이
 * 아닌 앱 커스텀 스킴으로 돌려보낼 수 있게 한다.
 *
 * <p>표준 {@link DefaultOAuth2AuthorizationRequestResolver} 에 위임해 인가요청과 state 를 그대로
 * 생성하므로, state 기반 CSRF 보호는 손상 없이 유지된다(우선순위 2). nonce 는 소셜 프로바이더로
 * 전송되지 않고(additionalParameters 가 아님) 서버 측 저장소에만 남아, 콜백 URL 에 노출되지 않는다.</p>
 */
@Component
public class NonceCapturingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** 프론트가 로그인 시작 시 전달하는 개시 바인딩 nonce 파라미터명. */
    public static final String NONCE_PARAM = "nonce";

    /** 클라이언트 타입 파라미터명. 값이 {@link #APP_CLIENT} 일 때만 앱 콜백으로 돌아간다. */
    public static final String CLIENT_PARAM = "client";

    /** 앱(모바일) 클라이언트를 뜻하는 {@link #CLIENT_PARAM} 값. */
    public static final String APP_CLIENT = "app";

    /**
     * 앱에서 시작한 로그인임을 state 에 남기는 접미사.
     *
     * <p>서버 측 저장소를 하나 더 두는 대신 state 에 실어 왕복시킨다. state 는 프로바이더가 그대로
     * 되돌려주고 인가요청 쿠키와 대조되므로 위조되면 로그인 자체가 실패한다. 구분자로 {@code '.'} 를
     * 쓰는 이유는 Spring 이 만드는 base64url state 에 절대 등장하지 않아 경계가 모호하지 않기 때문이다.</p>
     */
    public static final String APP_STATE_SUFFIX = ".app";

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
        return bindLoginContext(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return bindLoginContext(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest bindLoginContext(OAuth2AuthorizationRequest authorizationRequest,
                                                        HttpServletRequest request) {
        // 로그인 시작 경로가 아니면 delegate 가 null 을 반환한다. 그대로 통과시킨다.
        if (authorizationRequest == null) {
            return null;
        }
        OAuth2AuthorizationRequest bound = markAppClient(authorizationRequest, request);

        // nonce 는 최종 state(앱이면 접미사가 붙은 값)에 묶어야 콜백에서 회수된다.
        String nonce = request.getParameter(NONCE_PARAM);
        if (nonce != null && !nonce.isBlank()) {
            nonceStore.store(bound.getState(), nonce);
        }
        return bound;
    }

    /** 앱에서 시작한 로그인이면 state 에 접미사를 붙인다. 인가요청 URI 도 새 state 로 다시 만들어진다. */
    private OAuth2AuthorizationRequest markAppClient(OAuth2AuthorizationRequest authorizationRequest,
                                                     HttpServletRequest request) {
        if (!APP_CLIENT.equalsIgnoreCase(request.getParameter(CLIENT_PARAM))) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .state(authorizationRequest.getState() + APP_STATE_SUFFIX)
                .build();
    }
}
