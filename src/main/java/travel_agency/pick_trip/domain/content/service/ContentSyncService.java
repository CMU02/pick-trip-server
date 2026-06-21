package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiSyncResponse;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 콘텐츠 동기화 (수집 7단계). {@code /areaBasedSyncList2}로 변경분을 감지해
 * 이미 적재된 콘텐츠의 노출 상태({@link DataStatus})를 갱신한다.
 *
 * <p>{@code showflag=0}이면 비노출({@code INACTIVE}), 그 외에는 {@code ACTIVE}로 둔다.
 * 아직 적재되지 않은 콘텐츠는 수집 배치가 담당하므로 동기화에서는 건너뛴다. 호출 실패 시
 * 예외를 전파하지 않고 기존 데이터를 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSyncService {

    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TravelContentRepository travelContentRepository;

    /** 지역 변경분을 반영한다. 반환값은 상태가 바뀐 건수. */
    @Transactional
    public int syncRegion(Region region) {
        TourApiSyncResponse response;
        try {
            response = tourApiClient.getAreaBasedSyncList(
                    region.getAreaCode(), region.getSigunguCode(), null, null, 1, PAGE_SIZE);
        } catch (FeignException e) {
            log.warn("[동기화] {} 조회 실패 - 건너뜀: {}", region, e.getMessage());
            return 0;
        }
        if (response != null && response.isError()) {
            log.warn("[동기화] {} TourAPI 오류 응답 code={} msg={} - 건너뜀",
                    region, response.resultCode(), response.resultMsg());
            return 0;
        }

        int updated = 0;
        for (TourApiSyncResponse.Item item : response.changes()) {
            if (applyChange(item)) {
                updated++;
            }
        }
        log.info("[동기화] {} 지역 {}건 반영", region, updated);
        return updated;
    }

    private boolean applyChange(TourApiSyncResponse.Item item) {
        if (item.contentid() == null || item.contentid().isBlank()) {
            return false;
        }
        DataStatus next = "0".equals(item.showflag()) ? DataStatus.INACTIVE : DataStatus.ACTIVE;
        return travelContentRepository.findById(item.contentid())
                .filter(content -> content.getDataStatus() != next)
                .map(content -> {
                    content.changeDataStatus(next);
                    travelContentRepository.save(content);
                    return true;
                })
                .orElse(false);
    }
}
