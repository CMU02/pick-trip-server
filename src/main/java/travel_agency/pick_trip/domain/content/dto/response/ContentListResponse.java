package travel_agency.pick_trip.domain.content.dto.response;

import java.util.List;

public record ContentListResponse(
        int totalCount,
        int page,
        int size,
        List<ContentSummaryResponse> items
) {}
