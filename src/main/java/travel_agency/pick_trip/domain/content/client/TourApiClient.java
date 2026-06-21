package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.TourApiCodeResponse;
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
    TourApiDetailCommonResponse getDetailCommon(@RequestParam String contentId);

    @GetMapping("/detailIntro2")
    TourApiDetailIntroResponse getDetailIntro(
            @RequestParam String contentId,
            @RequestParam String contentTypeId
    );

    @GetMapping("/detailImage2")
    TourApiDetailImageResponse getDetailImage(@RequestParam String contentId);

    // --- 코드 seed (수집 2·3단계) ---

    /** 지역 코드. {@code areaCode}가 없으면 시도, 있으면 해당 시도의 시군구 목록. */
    @GetMapping("/areaCode2")
    TourApiCodeResponse getAreaCode(
            @RequestParam(required = false) String areaCode,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    /** 법정동 코드. {@code lDongRegnCd}가 없으면 시도, 있으면 해당 시도의 시군구 목록. */
    @GetMapping("/ldongCode2")
    TourApiCodeResponse getLdongCode(
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    /** 관광 타입 분류 코드. 상위 코드가 없으면 대분류, 있으면 하위 분류 목록. */
    @GetMapping("/categoryCode2")
    TourApiCodeResponse getCategoryCode(
            @RequestParam(required = false) String cat1,
            @RequestParam(required = false) String cat2,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );

    /** 신분류체계 코드. 상위 코드가 없으면 1Depth, 있으면 하위 Depth 목록. */
    @GetMapping("/lclsSystmCode2")
    TourApiCodeResponse getLclsSystmCode(
            @RequestParam(required = false) String lclsSystm1,
            @RequestParam(required = false) String lclsSystm2,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );
}
