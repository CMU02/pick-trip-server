package travel_agency.pick_trip.gloal.scheduler;

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
}
