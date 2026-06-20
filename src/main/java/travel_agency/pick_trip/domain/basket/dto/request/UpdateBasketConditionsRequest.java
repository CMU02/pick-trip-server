package travel_agency.pick_trip.domain.basket.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Set;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 여행 조건 저장/갱신 요청. 전달된 값으로 조건을 전체 교체하며, 모든 필드는 선택값이다.
 */
public record UpdateBasketConditionsRequest(
        Region region,
        LocalDate travelDate,
        @Positive Integer duration,
        Set<TravelCondition> companions
) {
}
