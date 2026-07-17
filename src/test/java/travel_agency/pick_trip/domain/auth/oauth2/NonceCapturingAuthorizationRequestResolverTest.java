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
import travel_agency.pick_trip.domain.auth.oauth2.exchange.OAuthNonceStore;

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
