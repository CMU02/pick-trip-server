package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Set;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 여행 조건 저장/갱신 요청. 전달된 값으로 조건을 전체 교체한다.
 *
 * <p>{@code duration} 은 필수다. 전체 교체 방식이라 누락하면 저장돼 있던 값이 그대로 지워지는데,
 * 요청은 200 으로 성공하고 실패는 한참 뒤 일정 생성에서 {@code ITINERARY_INPUT_INSUFFICIENT} 로만
 * 드러나 원인을 찾기 어렵다. 그래서 요청 시점에 거부한다.
 */
public record UpdateBasketConditionsRequest(
        Region region,
        LocalDate travelDate,
        @NotNull @Positive Integer duration,
        Set<TravelCondition> companions
) {
}
