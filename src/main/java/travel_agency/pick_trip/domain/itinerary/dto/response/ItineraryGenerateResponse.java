package travel_agency.pick_trip.domain.itinerary.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;

/**
 * AI 일정 생성 미리보기 응답.
 * 아직 저장 전 상태이며, 저장은 별도 요청(POST /api/v1/itineraries)으로 처리한다.
 * 각 장소에는 AI가 생성한 배치 이유(reason)가 함께 포함된다.
 */
public record ItineraryGenerateResponse(
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        List<Day> days
) {

    public record Day(
            int dayIndex,
            List<Item> items
    ) {
    }

    public record Item(
            String contentId,
            String title,
            int order,
            String reason
    ) {
    }

    /**
     * AI 결과를 미리보기 응답으로 변환한다.
     * 장소 표시명(title)은 AI 응답이 아니라 바구니 스냅샷에서 contentId 로 매핑한다.
     */
    public static ItineraryGenerateResponse from(Basket basket, AiItineraryResult result) {
        Map<String, String> titleByContentId = basket.getItems().stream()
                .collect(Collectors.toMap(
                        BasketItem::getContentId,
                        item -> item.getTitle() == null ? "" : item.getTitle(),
                        (a, b) -> a
                ));

        List<Day> days = result.days().stream()
                .map(day -> new Day(
                        day.dayIndex(),
                        day.items().stream()
                                .map(item -> new Item(
                                        item.contentId(),
                                        titleByContentId.getOrDefault(item.contentId(), null),
                                        item.order(),
                                        item.reason()
                                ))
                                .toList()
                ))
                .toList();

        return new ItineraryGenerateResponse(
                result.title(),
                basket.getRegion(),
                basket.getTravelDate(),
                basket.getDuration(),
                days
        );
    }
}
