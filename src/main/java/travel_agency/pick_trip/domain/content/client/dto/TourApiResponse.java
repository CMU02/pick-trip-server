package travel_agency.pick_trip.domain.content.client.dto;

import java.util.Set;

/**
 * TourAPI 표준 응답 공통 계약. TourAPI는 트래픽 한도 초과·인증키 오류 등에서도
 * HTTP 200 으로 응답하고 {@code response.header.resultCode} 로만 오류를 알린다.
 * 따라서 HTTP 상태가 아닌 결과 코드로 정상 여부를 판별한다.
 */
public interface TourApiResponse {

    /** 정상 결과 코드. 신/구 버전 표기를 모두 허용한다. */
    Set<String> SUCCESS_CODES = Set.of("0000", "00", "0");

    /** {@code response.header.resultCode} (헤더가 없으면 null). */
    String resultCode();

    /** {@code response.header.resultMsg} (헤더가 없으면 null). */
    String resultMsg();

    /**
     * 오류 응답이면 true. 헤더가 없으면(코드 null) 과거 호환을 위해 정상으로 본다.
     * TourAPI 한도 초과·키 오류는 HTTP 200 + 비정상 코드로 오므로 이 값으로 걸러야 한다.
     */
    default boolean isError() {
        String code = resultCode();
        return code != null && !SUCCESS_CODES.contains(code);
    }
}
