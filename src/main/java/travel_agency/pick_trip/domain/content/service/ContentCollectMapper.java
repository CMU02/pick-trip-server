package travel_agency.pick_trip.domain.content.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.ImageSource;

/**
 * TourAPI 수집 응답 → 엔티티 변환 보조. 좌표·주소 파싱과 안전한 응답 추출을 담당한다.
 * (위도=mapy, 경도=mapx)
 */
@Component
public class ContentCollectMapper {

    public List<TourApiListResponse.Item> listItems(TourApiListResponse response) {
        return Optional.ofNullable(response)
                .map(TourApiListResponse::response)
                .map(TourApiListResponse.Response::body)
                .map(TourApiListResponse.Body::items)
                .map(TourApiListResponse.Items::item)
                .orElse(Collections.emptyList());
    }

    public TourApiDetailCommonResponse.Item firstCommon(TourApiDetailCommonResponse response) {
        return Optional.ofNullable(response)
                .map(TourApiDetailCommonResponse::response)
                .map(TourApiDetailCommonResponse.Response::body)
                .map(TourApiDetailCommonResponse.Body::items)
                .map(TourApiDetailCommonResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    public TourApiDetailIntroResponse.Item firstIntro(TourApiDetailIntroResponse response) {
        return Optional.ofNullable(response)
                .map(TourApiDetailIntroResponse::response)
                .map(TourApiDetailIntroResponse.Response::body)
                .map(TourApiDetailIntroResponse.Body::items)
                .map(TourApiDetailIntroResponse.Items::item)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    /** {@code /detailImage2} 응답을 {@link ContentImage} 목록으로 변환한다 (TourAPI 출처). */
    public List<ContentImage> toContentImages(TourApiDetailImageResponse response) {
        return Optional.ofNullable(response)
                .map(TourApiDetailImageResponse::response)
                .map(TourApiDetailImageResponse.Response::body)
                .map(TourApiDetailImageResponse.Body::items)
                .map(TourApiDetailImageResponse.Items::item)
                .orElse(Collections.emptyList())
                .stream()
                .filter(item -> item.originimgurl() != null && !item.originimgurl().isBlank())
                .map(item -> ContentImage.builder()
                        .source(ImageSource.TOUR_API)
                        .imageUrl(item.originimgurl())
                        .title(item.imgname())
                        .build())
                .toList();
    }

    /** 위도(mapy) 파싱. 비어 있거나 형식 오류면 null. */
    public Double parseLatitude(String mapy) {
        return parseDoubleOrNull(mapy);
    }

    /** 경도(mapx) 파싱. 비어 있거나 형식 오류면 null. */
    public Double parseLongitude(String mapx) {
        return parseDoubleOrNull(mapx);
    }

    public String buildAddress(String addr1, String addr2) {
        if (addr1 == null || addr1.isBlank()) {
            return null;
        }
        if (addr2 == null || addr2.isBlank()) {
            return addr1;
        }
        return addr1 + " " + addr2;
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
