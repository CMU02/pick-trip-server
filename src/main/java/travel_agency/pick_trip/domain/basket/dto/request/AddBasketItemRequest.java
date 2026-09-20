package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import travel_agency.pick_trip.domain.basket.entity.Priority;

/**
 * 바구니에 콘텐츠를 추가하는 요청.
 * contentId는 TourAPI ID이며 서버에서 별도 검증 없이 저장한다.
 * title/thumbnailUrl/contentTypeId는 목록 화면에서 확보한 표시용 스냅샷(선택값)이다.
 * desiredStayMinutes는 사용자가 직접 지정한 희망 체류시간(분, 선택값)이다.
 */
public record AddBasketItemRequest(
        @NotBlank String contentId,
        @NotNull Priority priority,
        String title,
        String thumbnailUrl,
        String contentTypeId,
        @Min(10) @Max(480) Integer desiredStayMinutes
) {
}
