package travel_agency.pick_trip.domain.itinerary.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 저장된 일정 상세 응답 (소유자용). 식별자와 고정 여부 등 편집에 필요한 정보를 포함한다.
 */
public record ItineraryResponse(
        UUID itineraryId,
        String title,
        Region region,
        LocalDate travelDate,
        Integer duration,
        LocalDateTime lastModifiedAt,
        List<Day> days
) {

    public record Day(
            UUID dayId,
            int dayIndex,
            List<Item> items
    ) {
    }

    public record Item(
            UUID itemId,
            String contentId,
            String title,
            int order,
            String reason,
            boolean pinned
    ) {
    }

    public static ItineraryResponse from(Itinerary itinerary) {
        List<Day> days = itinerary.getDays().stream()
                .map(day -> new Day(
                        day.getDayId(),
                        day.getDayIndex(),
                        day.getItems().stream()
                                .map(item -> new Item(
                                        item.getItemId(),
                                        item.getContentId(),
                                        item.getTitle(),
                                        item.getOrderIndex(),
                                        item.getReason(),
                                        item.isPinned()
                                ))
                                .toList()
                ))
                .toList();

        return new ItineraryResponse(
                itinerary.getItineraryId(),
                itinerary.getTitle(),
                itinerary.getRegion(),
                itinerary.getTravelDate(),
                itinerary.getDuration(),
                itinerary.getLastModifiedAt(),
                days
        );
    }
}
