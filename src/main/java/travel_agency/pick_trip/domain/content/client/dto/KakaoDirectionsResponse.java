package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Kakao Mobility 자동차 길찾기(출발지 1점 → 목적지 1점) 응답.
 * 첫 경로의 {@code result_code == 0} 이면 성공이며, 요약은 여러 목적지 길찾기와 같은 형태다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoDirectionsResponse(List<Route> routes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(
            @JsonProperty("result_code") Integer resultCode,
            KakaoMultiDestResponse.Summary summary
    ) {}
}
