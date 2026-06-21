package travel_agency.pick_trip.domain.content.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class TourApiFeignConfig {

    @Bean
    public RequestInterceptor tourApiRequestInterceptor(
            @Value("${tour-api.service-key}") String serviceKey) {
        return new TourApiRequestInterceptor(serviceKey);
    }

    @Bean
    public Decoder tourApiDecoder() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                // TourAPI는 결과 없을 때 items를 객체 대신 빈 문자열("")로 반환
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        return new JacksonDecoder(mapper);
    }

    /** 무한 대기로 인한 배치 행(hang) 방지: connect 5s / read 10s. */
    @Bean
    public Request.Options tourApiRequestOptions() {
        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }

    /** 일시적 5xx·타임아웃 대비 지수 백오프 재시도: 100ms ~ 1s, 최대 3회. */
    @Bean
    public Retryer tourApiRetryer() {
        return new Retryer.Default(100, TimeUnit.SECONDS.toMillis(1), 3);
    }

    /** 5xx 를 재시도 대상으로 변환한다(기본 ErrorDecoder는 Retry-After 없는 5xx를 재시도하지 않음). */
    @Bean
    public ErrorDecoder tourApiErrorDecoder() {
        return new TourApiErrorDecoder();
    }

}
