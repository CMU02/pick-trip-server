package travel_agency.pick_trip.domain.basket.dto.response;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 여행 바구니 응답 (여행 조건 + 담은 콘텐츠 목록).
 * 바구니가 없는 사용자는 {@link #empty()}로 빈 바구니를 반환한다.
 */
public record BasketResponse(
        UUID basketId,
        Conditions conditions,
        List<BasketItemResponse> items
) {

    /**
     * 여행 조건.
     */
    public record Conditions(
            Region region,
            LocalDate travelDate,
            Integer duration,
            Set<TravelCondition> companions
    ) {
        public static Conditions from(Basket basket) {
            return new Conditions(
                    basket.getRegion(),
                    basket.getTravelDate(),
                    basket.getDuration(),
                    basket.getCompanions()
            );
        }
    }

    public static BasketResponse from(Basket basket) {
        List<BasketItemResponse> items = basket.getItems().stream()
                .map(BasketItemResponse::from)
                .toList();
        return new BasketResponse(basket.getBasketId(), Conditions.from(basket), items);
    }

    /**
     * 바구니가 아직 없는 사용자를 위한 빈 응답.
     */
    public static BasketResponse empty() {
        return new BasketResponse(
                null,
                new Conditions(null, null, null, Collections.emptySet()),
                Collections.emptyList()
        );
    }
}
