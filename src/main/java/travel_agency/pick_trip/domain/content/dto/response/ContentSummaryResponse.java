package travel_agency.pick_trip.domain.content.dto.response;

import travel_agency.pick_trip.domain.content.entity.ContentCategory;

public record ContentSummaryResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String firstImage,
        double latitude,
        double longitude,
        ContentCategory category,
        String summary,
        boolean indoor,
        String region
) {
}
