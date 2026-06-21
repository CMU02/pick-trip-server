package travel_agency.pick_trip.gloal.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import travel_agency.pick_trip.domain.content.service.ContentSyncService;
import travel_agency.pick_trip.domain.content.service.ImageEnrichService;
import travel_agency.pick_trip.domain.content.service.PhotoSyncService;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 콘텐츠 동기화·이미지 보강 스케줄러 (수집 7단계). 모든 지역에 대해
 * {@link ContentSyncService}로 변경분을 반영하고 {@link ImageEnrichService}로 이미지를 보강한다.
 *
 * <p>{@code @Scheduled}는 {@code @EnableScheduling}이 활성화된 운영 프로파일에서만 동작한다
 * ({@link travel_agency.pick_trip.gloal.config.SchedulingConfig}). 갱신 실패 시 기존 데이터를 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentSyncScheduler {

    private final ContentSyncService contentSyncService;
    private final ImageEnrichService imageEnrichService;
    private final PhotoSyncService photoSyncService;

    /** 기본 정보 동기화 + 이미지 보강 + 관광사진 증분 동기화. 기본값: 매주 월요일 새벽 4시. */
    @Scheduled(cron = "${tour-api.sync.cron:0 0 4 * * MON}")
    public void syncAllRegions() {
        log.info("[스케줄] TourAPI 동기화·이미지 보강 시작");
        for (Region region : Region.values()) {
            contentSyncService.syncRegion(region);
            imageEnrichService.enrichRegion(region);
        }
        photoSyncService.syncRecentPhotos();
        log.info("[스케줄] TourAPI 동기화·이미지 보강 완료");
    }
}
