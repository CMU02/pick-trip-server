package travel_agency.pick_trip.domain.content.client;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TourApiErrorDecoder")
class TourApiErrorDecoderTest {

    private final ErrorDecoder decoder = new TourApiErrorDecoder();

    private Response responseWithStatus(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET, "/areaBasedList2",
                Map.<String, Collection<String>>of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Map.<String, Collection<String>>of())
                .build();
    }

    @Test
    @DisplayName("5xx 응답은 재시도 예외로 변환한다")
    void decode_5xx_재시도예외() {
        Exception e = decoder.decode("method", responseWithStatus(503));
        assertThat(e).isInstanceOf(RetryableException.class);
    }

    @Test
    @DisplayName("4xx 응답은 재시도 예외로 변환하지 않는다")
    void decode_4xx_비재시도() {
        Exception e = decoder.decode("method", responseWithStatus(400));
        assertThat(e).isNotInstanceOf(RetryableException.class);
    }
}
