package travel_agency.pick_trip.gloal.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.service.ContentSyncService;
import travel_agency.pick_trip.domain.content.service.ImageEnrichService;
import travel_agency.pick_trip.domain.content.service.PhotoSyncService;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentSyncScheduler")
class ContentSyncSchedulerTest {

    @Mock private ContentSyncService contentSyncService;
    @Mock private ImageEnrichService imageEnrichService;
    @Mock private PhotoSyncService photoSyncService;

    @InjectMocks private ContentSyncScheduler contentSyncScheduler;

    @Test
    @DisplayName("모든 지역 동기화·이미지 보강과 관광사진 증분 동기화를 위임한다")
    void syncAllRegions_모든지역_위임() {
        contentSyncScheduler.syncAllRegions();

        for (Region region : Region.values()) {
            verify(contentSyncService).syncRegion(region);
            verify(imageEnrichService).enrichRegion(region);
        }
        verify(contentSyncService, times(Region.values().length)).syncRegion(org.mockito.ArgumentMatchers.any());
        verify(imageEnrichService, times(Region.values().length)).enrichRegion(org.mockito.ArgumentMatchers.any());
        verify(photoSyncService).syncRecentPhotos();
    }

    @Test
    @DisplayName("한 지역 단계가 실패해도 나머지 지역과 관광사진 동기화를 계속한다")
    void syncAllRegions_단계실패_격리() {
        Region first = Region.values()[0];
        given(contentSyncService.syncRegion(first)).willThrow(new RuntimeException("동기화 실패"));

        contentSyncScheduler.syncAllRegions();

        // 첫 지역의 동기화가 예외로 끝나도 같은 지역 이미지 보강, 다른 지역, 관광사진 동기화가 모두 호출된다.
        for (Region region : Region.values()) {
            verify(contentSyncService).syncRegion(region);
            verify(imageEnrichService).enrichRegion(region);
        }
        verify(photoSyncService).syncRecentPhotos();
    }
}
