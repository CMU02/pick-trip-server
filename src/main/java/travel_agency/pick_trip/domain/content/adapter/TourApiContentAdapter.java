package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

@Component
@RequiredArgsConstructor
public class TourApiContentAdapter {

    private final TourApiClient tourApiClient;
    private final TourApiContentMapper mapper;

    public ContentListResponse fetchList(ContentListRequest request, Region region) {
        // TourAPI pageNo는 1-indexed
        int pageNo = request.page() + 1;
        try {
            TourApiListResponse raw;
            if (request.keyword() != null && !request.keyword().isBlank()) {
                raw = tourApiClient.searchByKeyword(
                        request.keyword(),
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        request.contentTypeId(),
                        pageNo,
                        request.size()
                );
            } else {
                raw = tourApiClient.getAreaBasedList(
                        region.getAreaCode(),
                        region.getSigunguCode(),
                        request.contentTypeId(),
                        pageNo,
                        request.size()
                );
            }
            return mapper.toListResponse(raw, request.page(), request.size());
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    public ContentDetailResponse fetchDetail(String contentId) {
        try {
            TourApiDetailCommonResponse common = tourApiClient.getDetailCommon(contentId);

            boolean isEmpty = common.response() == null
                    || common.response().body() == null
                    || common.response().body().items() == null
                    || common.response().body().items().item() == null
                    || common.response().body().items().item().isEmpty();

            if (isEmpty) {
                throw new ContentException(ErrorCode.CONTENT_NOT_FOUND);
            }

            String contentTypeId = common.response().body().items().item().get(0).contenttypeid();
            TourApiDetailIntroResponse intro = tourApiClient.getDetailIntro(contentId, contentTypeId);
            TourApiDetailImageResponse image = tourApiClient.getDetailImage(contentId);

            return mapper.toDetailResponse(common, intro, image);
        } catch (ContentException e) {
            throw e;
        } catch (FeignException e) {
            throw new ContentException(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }
}
