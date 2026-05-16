package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;

@FeignClient(
        name = "tour-api",
        url = "${tour-api.base-url}",
        configuration = TourApiFeignConfig.class
)
public interface TourApiClient {

    @GetMapping("/areaBasedList2")
    TourApiListResponse getAreaBasedList(
            @RequestParam String areaCode,
            @RequestParam String sigunguCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    @GetMapping("/searchKeyword2")
    TourApiListResponse searchByKeyword(
            @RequestParam String keyword,
            @RequestParam String areaCode,
            @RequestParam String sigunguCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    @GetMapping("/detailCommon2")
    TourApiDetailCommonResponse getDetailCommon(
            @RequestParam String contentId,
            @RequestParam String defaultYN,
            @RequestParam String overviewYN
    );

    @GetMapping("/detailIntro2")
    TourApiDetailIntroResponse getDetailIntro(
            @RequestParam String contentId,
            @RequestParam String contentTypeId
    );

    @GetMapping("/detailImage2")
    TourApiDetailImageResponse getDetailImage(
            @RequestParam String contentId,
            @RequestParam String imageYN,
            @RequestParam String subImageYN
    );
}
