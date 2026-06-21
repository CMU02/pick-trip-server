package travel_agency.pick_trip.domain.share.dto.response;

import java.time.LocalDate;
import java.util.List;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 공유 토큰으로 조회하는 공개 일정 응답 (읽기 전용).
 * 소유자·내부 식별자·고정 여부 등 편집용 정보는 노출하지 않는다.
 */
public record SharedItineraryResponse(
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

    public static SharedItineraryResponse from(Itinerary itinerary) {
        List<Day> days = itinerary.getDays().stream()
                .map(day -> new Day(
                        day.getDayIndex(),
                        day.getItems().stream()
                                .map(item -> new Item(
                                        item.getContentId(),
                                        item.getTitle(),
                                        item.getOrderIndex(),
                                        item.getReason()
                                ))
                                .toList()
                ))
                .toList();

        return new SharedItineraryResponse(
                itinerary.getTitle(),
                itinerary.getRegion(),
                itinerary.getTravelDate(),
                itinerary.getDuration(),
                days
        );
    }
}
