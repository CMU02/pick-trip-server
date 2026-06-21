package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiSyncResponse;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentSyncService")
class ContentSyncServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TravelContentRepository travelContentRepository;

    @InjectMocks private ContentSyncService contentSyncService;

    private static final Region REGION = Region.HADONG; // areaCode=36, sigunguCode=18
    private static final String CONTENT_ID = "126508";

    private TravelContent activeContent() {
        return TravelContent.builder()
                .sourceContentId(CONTENT_ID)
                .title("화개장터")
                .region(Region.HADONG)
                .dataStatus(DataStatus.ACTIVE)
                .build();
    }

    private TourApiSyncResponse syncResponse(String showflag) {
        return new TourApiSyncResponse(new TourApiSyncResponse.Response(
                new TourApiSyncResponse.Body(new TourApiSyncResponse.Items(List.of(
                        new TourApiSyncResponse.Item(CONTENT_ID, "12", "화개장터", "20260601", showflag))),
                        100, 1, 1)));
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("showflag=0 이면 적재 콘텐츠를 INACTIVE 로 변경한다")
    void syncRegion_showflag0_INACTIVE() {
        TravelContent content = activeContent();
        given(tourApiClient.getAreaBasedSyncList("36", "18", null, null, 1, 100))
                .willReturn(syncResponse("0"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(content));

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isEqualTo(1);
        assertThat(content.getDataStatus()).isEqualTo(DataStatus.INACTIVE);
        verify(travelContentRepository).save(content);
    }

    @Test
    @DisplayName("상태 변화가 없으면 저장하지 않는다")
    void syncRegion_상태동일_미변경() {
        TravelContent content = activeContent();
        given(tourApiClient.getAreaBasedSyncList("36", "18", null, null, 1, 100))
                .willReturn(syncResponse("1"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(content));

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        assertThat(content.getDataStatus()).isEqualTo(DataStatus.ACTIVE);
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("아직 적재되지 않은 콘텐츠는 건너뛴다")
    void syncRegion_미적재_건너뜀() {
        given(tourApiClient.getAreaBasedSyncList("36", "18", null, null, 1, 100))
                .willReturn(syncResponse("0"));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("동기화 조회가 실패하면 예외를 전파하지 않는다")
    void syncRegion_조회실패_미반영() {
        given(tourApiClient.getAreaBasedSyncList(eq("36"), eq("18"), isNull(), isNull(), eq(1), eq(100)))
                .willThrow(feignError());

        int updated = contentSyncService.syncRegion(REGION);

        assertThat(updated).isZero();
        verify(travelContentRepository, never()).save(any());
    }
}
