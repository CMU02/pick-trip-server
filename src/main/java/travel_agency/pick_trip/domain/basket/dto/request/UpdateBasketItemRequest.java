package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import travel_agency.pick_trip.domain.basket.entity.Priority;

/**
 * 바구니 항목의 부분 갱신 요청.
 * priority·desiredStayMinutes 모두 선택값이며, null 인 필드는 변경하지 않는다.
 */
public record UpdateBasketItemRequest(
        Priority priority,
        @Min(10) @Max(480) Integer desiredStayMinutes
) {
}
