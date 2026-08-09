package travel_agency.pick_trip.domain.favorite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 콘텐츠를 찜하는 요청.
 * contentId는 TourAPI ID이며 서버에서 별도 검증 없이 저장한다.
 * title/address 외 나머지는 목록 화면에서 확보한 표시용 스냅샷(선택값)이다.
 */
public record AddFavoriteRequest(
        @NotBlank String contentId,
        @NotBlank String title,
        @NotBlank String address,
        String firstImage,
        ContentCategory category,
        String summary,
        Boolean indoor,
        @NotNull Region region
) {
}
