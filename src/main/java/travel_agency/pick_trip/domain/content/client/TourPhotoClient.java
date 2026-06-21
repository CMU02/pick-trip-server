package travel_agency.pick_trip.domain.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import travel_agency.pick_trip.domain.content.client.dto.TourApiPhotoResponse;

/**
 * 관광사진 정보_GW Feign 클라이언트. 콘텐츠 대표 이미지가 부족할 때 보조 이미지를 검색한다.
 * 국문 관광정보 서비스와 base-url 은 다르지만 동일 인증키·공통 파라미터({@link TourApiFeignConfig})를 사용한다.
 */
@FeignClient(
        name = "tour-photo-api",
        url = "${tour-api.photo-base-url}",
        configuration = TourApiFeignConfig.class
)
public interface TourPhotoClient {

    /** 지역명/키워드 기반 보조 이미지 검색. */
    @GetMapping("/gallerySearchList1")
    TourApiPhotoResponse searchGallery(
            @RequestParam String keyword,
            @RequestParam int pageNo,
            @RequestParam int numOfRows
    );
}
