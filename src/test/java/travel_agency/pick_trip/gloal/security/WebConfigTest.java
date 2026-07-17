package travel_agency.pick_trip.gloal.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebConfig")
class WebConfigTest {

    private static final String ALLOWED_ORIGINS = "http://localhost:3000,https://picktrip.app";

    private CorsConfiguration corsConfiguration(boolean allowCredentials) {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "allowedOrigins", ALLOWED_ORIGINS);
        ReflectionTestUtils.setField(webConfig, "allowCredentials", allowCredentials);

        CorsConfigurationSource source = webConfig.corsConfigurationSource();
        UrlBasedCorsConfigurationSource urlSource = (UrlBasedCorsConfigurationSource) source;
        return urlSource.getCorsConfigurations().get("/**");
    }

    @Nested
    @DisplayName("허용 헤더")
    class AllowedHeaders {

        @Test
        @DisplayName("Authorization/Content-Type 헤더만 허용하고 와일드카드는 허용하지 않는다")
        void allowedHeaders_containsOnlyAuthorizationAndContentType() {
            // when
            CorsConfiguration cfg = corsConfiguration(true);

            // then
            assertThat(cfg.getAllowedHeaders())
                    .containsExactlyInAnyOrder("Authorization", "Content-Type")
                    .doesNotContain("*");
        }
    }

    @Nested
    @DisplayName("허용 오리진")
    class AllowedOrigins {

        @Test
        @DisplayName("프로퍼티로 주입한 오리진만 허용하고 와일드카드는 허용하지 않는다")
        void allowedOriginPatterns_containsConfiguredOriginsOnly() {
            // when
            CorsConfiguration cfg = corsConfiguration(true);

            // then
            assertThat(cfg.getAllowedOriginPatterns())
                    .contains("http://localhost:3000", "https://picktrip.app")
                    .doesNotContain("*");
        }
    }

    @Nested
    @DisplayName("허용 메서드")
    class AllowedMethods {

        @Test
        @DisplayName("GET/POST/PUT/DELETE/PATCH/OPTIONS 메서드를 허용한다")
        void allowedMethods_containsExpectedMethods() {
            // when
            CorsConfiguration cfg = corsConfiguration(true);

            // then
            assertThat(cfg.getAllowedMethods())
                    .contains("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        }
    }

    @Nested
    @DisplayName("자격 증명 허용 여부")
    class AllowCredentials {

        @Test
        @DisplayName("allowCredentials 필드가 true면 자격 증명을 허용한다")
        void allowCredentials_trueWhenFieldTrue() {
            // when
            CorsConfiguration cfg = corsConfiguration(true);

            // then
            assertThat(cfg.getAllowCredentials()).isTrue();
        }

        @Test
        @DisplayName("allowCredentials 필드가 false면 자격 증명을 허용하지 않는다")
        void allowCredentials_falseWhenFieldFalse() {
            // when
            CorsConfiguration cfg = corsConfiguration(false);

            // then
            assertThat(cfg.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
        }
    }
}
