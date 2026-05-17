package travel_agency.pick_trip.domain.content.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
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

}
