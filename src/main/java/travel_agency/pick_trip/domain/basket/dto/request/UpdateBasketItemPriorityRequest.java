package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.NotNull;
import travel_agency.pick_trip.domain.basket.entity.Priority;

/**
 * 바구니 항목의 우선순위 변경 요청.
 */
public record UpdateBasketItemPriorityRequest(
        @NotNull Priority priority
) {
}
