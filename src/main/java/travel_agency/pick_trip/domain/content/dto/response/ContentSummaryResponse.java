package travel_agency.pick_trip.domain.content.dto.response;

public record ContentSummaryResponse(
        String contentId,
        String title,
        int contentTypeId,
        String address,
        String firstImage,
        double latitude,
        double longitude
) {
}
