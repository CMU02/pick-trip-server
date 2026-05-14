package travel_agency.pick_trip.domain.auth.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * TourAPI 전용 Feign 설정.
 * @Configuration 없이 @FeignClient(configuration = TourApiFeignConfig.class)로만 적용해
 * OAuth 등 다른 Feign 클라이언트에 serviceKey가 주입되지 않도록 한다.
 */
public class TourApiFeignConfig {

    @Value("${public-data-portal.key.encode}")
    private String serviceKey;

    @Bean
    public RequestInterceptor tourApiRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.query("serviceKey", serviceKey);
            requestTemplate.query("_type", "json");
        };
    }
}
