package travel_agency.pick_trip.domain.content.client;

import feign.FeignException;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

/**
 * TourAPI 호출의 5xx 응답을 재시도 대상({@link RetryableException})으로 변환한다.
 * Feign 기본 {@link ErrorDecoder.Default}는 {@code Retry-After} 헤더가 없는 5xx 를 재시도하지 않으므로,
 * 일시적 서버 오류에 대해 {@link feign.Retryer}가 동작하도록 별도로 변환한다. 4xx 는 그대로 전파한다.
 */
public class TourApiErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception decoded = defaultDecoder.decode(methodKey, response);
        if (response.status() >= 500 && response.status() <= 599) {
            String message = decoded instanceof FeignException fe
                    ? fe.getMessage()
                    : "TourAPI 서버 오류 status=" + response.status();
            return new RetryableException(
                    response.status(),
                    message,
                    response.request().httpMethod(),
                    (Long) null,
                    response.request());
        }
        return decoded;
    }
}
