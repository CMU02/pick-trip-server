package travel_agency.pick_trip.domain.content.dto.request;

public record ContentListRequest(
        String region,
        String contentTypeId,
        String keyword,
        CompanionType companion,
        Boolean indoorOnly,
        int page,
        int size
) {
    public ContentListRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 40) size = 20;
    }
}
