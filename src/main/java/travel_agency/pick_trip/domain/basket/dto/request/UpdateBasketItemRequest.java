package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import travel_agency.pick_trip.domain.basket.entity.Priority;

/**
 * 바구니 항목의 부분 갱신 요청.
 * priority·desiredStayMinutes 모두 선택값이며, null 인 필드는 변경하지 않는다.
 * 단, 최소 하나는 있어야 한다(둘 다 없는 빈 요청은 오타·누락을 조용히 통과시키므로 거부한다).
 */
public record UpdateBasketItemRequest(
        Priority priority,
        @Min(10) @Max(480) Integer desiredStayMinutes
) {
    @AssertTrue(message = "priority 또는 desiredStayMinutes 중 하나는 있어야 합니다.")
    public boolean isAtLeastOneFieldPresent() {
        return priority != null || desiredStayMinutes != null;
    }
}
