package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.KakaoDirectionsResponse;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestRequest;
import travel_agency.pick_trip.domain.content.client.dto.KakaoMultiDestResponse;

/**
 * Kakao Mobility 길찾기. 근처 콘텐츠를 실제 도로 거리로 정렬하기 위해
 * 기준 좌표 1점 → 주변 후보 N점(최대 30)의 도로 거리·소요 시간을 1콜로 조회한다.
 */
@FeignClient(
        name = "kakao-mobility",
        url = "${kakao-mobility.base-url}",
        configuration = KakaoMobilityFeignConfig.class
)
public interface KakaoMobilityClient {

    @PostMapping("/v1/destinations/directions")
    KakaoMultiDestResponse getMultiDestinationDirections(@RequestBody KakaoMultiDestRequest request);

    /**
     * 출발지 1점 → 목적지 1점 자동차 길찾기. 여러 목적지 길찾기의 반경(10km) 밖 구간을 채울 때 쓴다.
     *
     * @param origin      {@code "경도,위도"}
     * @param destination {@code "경도,위도"}
     * @param summary     true 면 경로 상세 없이 요약(거리·시간)만 받는다
     */
    @GetMapping("/v1/directions")
    KakaoDirectionsResponse getDirections(@RequestParam("origin") String origin,
                                          @RequestParam("destination") String destination,
                                          @RequestParam("summary") boolean summary);
}
