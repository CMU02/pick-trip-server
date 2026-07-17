package travel_agency.pick_trip.gloal.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카카오는 CommonOAuth2Provider 내장 항목이 아니라 provider 를 직접 정의해야 하므로,
 * application.yaml 의 설정이 실제로 ClientRegistration 으로 바인딩되는지 검증한다.
 */
@DisplayName("카카오 OAuth2 설정")
class OAuth2ConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class))
            .withPropertyValues(
                    // application.yaml 과 동일한 값
                    "spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
                    "spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
                    "spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
                    "spring.security.oauth2.client.provider.kakao.user-name-attribute=id",
                    "spring.security.oauth2.client.registration.kakao.client-name=Kakao",
                    "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
                    "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
                    "spring.security.oauth2.client.registration.kakao.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
                    "spring.security.oauth2.client.registration.kakao.scope=profile_nickname,profile_image,account_email",
                    // 프로필 파일에서 주입되는 값
                    "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
                    "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
            );

    @Test
    @DisplayName("카카오 registration 이 ClientRegistration 으로 바인딩된다")
    void kakaoRegistrationBinds() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            ClientRegistration kakao = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("kakao");

            assertThat(kakao).isNotNull();
            assertThat(kakao.getProviderDetails().getAuthorizationUri())
                    .isEqualTo("https://kauth.kakao.com/oauth/authorize");
            assertThat(kakao.getProviderDetails().getTokenUri())
                    .isEqualTo("https://kauth.kakao.com/oauth/token");
            assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                    .isEqualTo("id");
            assertThat(kakao.getScopes())
                    .containsExactlyInAnyOrder("profile_nickname", "profile_image", "account_email");
        });
    }

    @Test
    @DisplayName("카카오 토큰 요청은 client_secret 을 본문으로 보낸다")
    void kakaoUsesClientSecretPost() {
        runner.run(context -> {
            ClientRegistration kakao = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("kakao");

            // Basic 헤더 방식이면 카카오가 401 을 반환한다.
            assertThat(kakao.getClientAuthenticationMethod())
                    .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        });
    }

    @Test
    @DisplayName("카카오는 CommonOAuth2Provider 내장이 아니므로 provider 정의가 필수다")
    void kakaoIsNotBuiltIn() {
        assertThat(CommonOAuth2Provider.values())
                .noneMatch(provider -> provider.name().equalsIgnoreCase("kakao"));
    }
}
