package travel_agency.pick_trip.domain.content.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.region.Region;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final TourApiContentAdapter adapter;

    public ContentListResponse getContents(ContentListRequest request) {
        Region region = Region.fromCode(request.region());
        return adapter.fetchList(request, region);
    }

    public ContentDetailResponse getContentDetail(String contentId) {
        return adapter.fetchDetail(contentId);
    }
}
