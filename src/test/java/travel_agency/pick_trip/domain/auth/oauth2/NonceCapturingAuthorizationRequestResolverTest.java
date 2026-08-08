package travel_agency.pick_trip.domain.auth.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NonceCapturingAuthorizationRequestResolver")
class NonceCapturingAuthorizationRequestResolverTest {

    @Mock private OAuthNonceStore nonceStore;

    private NonceCapturingAuthorizationRequestResolver resolver;

    @BeforeEach
    void setUp() {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .build();
        resolver = new NonceCapturingAuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(google), nonceStore);
    }

    private MockHttpServletRequest authorizationRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        return request;
    }

    @Test
    @DisplayName("nonce 파라미터가 있으면 생성된 state에 묶어 저장한다")
    void resolve_withNonce_storesNonceKeyedByState() {
        // given
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("nonce", "browser-nonce");

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        assertThat(result).isNotNull();
        then(nonceStore).should().store(eq(result.getState()), eq("browser-nonce"));
    }

    @Test
    @DisplayName("nonce 파라미터가 없으면 저장하지 않는다")
    void resolve_withoutNonce_doesNotStore() {
        // given
        MockHttpServletRequest request = authorizationRequest();

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        assertThat(result).isNotNull();
        then(nonceStore).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("client=app 이면 state에 앱 표식이 붙고 인가요청 URI도 그 state로 다시 만들어진다")
    void resolve_appClient_marksState() {
        // given
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("client", "app");

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getState())
                .endsWith(NonceCapturingAuthorizationRequestResolver.APP_STATE_SUFFIX);
        // 프로바이더로 보내는 URI 의 state 가 갱신되지 않으면 콜백에서 state 검증이 깨진다.
        String stateInUri = UriComponentsBuilder.fromUriString(result.getAuthorizationRequestUri())
                .build().getQueryParams().getFirst("state");
        assertThat(UriUtils.decode(stateInUri, StandardCharsets.UTF_8)).isEqualTo(result.getState());
    }

    @Test
    @DisplayName("client=app 이면 nonce는 표식이 붙은 state에 묶여 저장된다")
    void resolve_appClientWithNonce_storesNonceKeyedByMarkedState() {
        // given
        MockHttpServletRequest request = authorizationRequest();
        request.setParameter("client", "app");
        request.setParameter("nonce", "browser-nonce");

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        then(nonceStore).should().store(eq(result.getState()), eq("browser-nonce"));
    }

    @Test
    @DisplayName("client 파라미터가 없으면 state는 그대로 둔다")
    void resolve_withoutClient_keepsState() {
        // given
        MockHttpServletRequest request = authorizationRequest();

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        assertThat(result.getState())
                .doesNotEndWith(NonceCapturingAuthorizationRequestResolver.APP_STATE_SUFFIX);
    }

    @Test
    @DisplayName("인가 시작 경로가 아니면 null을 반환하고 저장하지 않는다")
    void resolve_nonAuthorizationPath_returnsNull() {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/contents");
        request.setServletPath("/api/v1/contents");
        request.setParameter("nonce", "browser-nonce");

        // when
        OAuth2AuthorizationRequest result = resolver.resolve(request);

        // then
        assertThat(result).isNull();
        then(nonceStore).shouldHaveNoInteractions();
    }
}
