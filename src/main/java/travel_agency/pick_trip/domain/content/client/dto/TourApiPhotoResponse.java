package travel_agency.pick_trip.domain.content.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 관광사진 정보_GW {@code /gallerySearchList1} 응답. 콘텐츠 대표 이미지가 부족할 때 보조 이미지 원천.
 * {@code galUseFlag=1}인 사진만 사용한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TourApiPhotoResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Items items, int numOfRows, int pageNo, int totalCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String galContentId,
            String galTitle,
            String galWebImageUrl,
            String galPhotographyMonth,
            String cpyrhtDivCd,
            String galUseFlag
    ) {}

    /** {@code galUseFlag=1}이고 이미지 URL이 있는 사진만 반환한다. */
    public List<Item> usablePhotos() {
        if (response == null
                || response.body() == null
                || response.body().items() == null
                || response.body().items().item() == null) {
            return List.of();
        }
        return response.body().items().item().stream()
                .filter(item -> "1".equals(item.galUseFlag()))
                .filter(item -> item.galWebImageUrl() != null && !item.galWebImageUrl().isBlank())
                .toList();
    }
}
