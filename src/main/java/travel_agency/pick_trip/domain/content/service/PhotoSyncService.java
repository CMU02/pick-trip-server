package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourPhotoClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiPhotoResponse;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.ImageSource;
import travel_agency.pick_trip.domain.content.repository.ContentImageRepository;

/**
 * 관광사진 증분 동기화 (수집 7단계 보조). {@code /gallerySyncDetailList1}로 변경된 사진을 받아
 * 저장된 {@link ImageSource#PHOTO_GALLERY} 이미지에 반영한다.
 *
 * <p>매칭은 {@code imageUrl}(= {@code galWebImageUrl}) 정확 대조로 한다. {@code galUseFlag != 1}이면
 * 매칭 이미지를 제거하고, 사용 가능하면 메타데이터를 갱신한다. 매칭되는 저장 이미지가 없으면 건너뛴다
 * (신규 보강은 {@link ImageEnrichService} 담당). 호출 실패 시 예외를 전파하지 않는다.
 *
 * <p>외부 조회는 트랜잭션 밖에서 수행하고, 반영(갱신·삭제)만 짧은 트랜잭션으로 감싼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoSyncService {

    private static final int PAGE_SIZE = 100;
    private static final int DEFAULT_LOOKBACK_DAYS = 7;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TourPhotoClient tourPhotoClient;
    private final ContentImageRepository contentImageRepository;
    private final TransactionTemplate transactionTemplate;

    /** 최근 {@value #DEFAULT_LOOKBACK_DAYS}일 변경분을 동기화한다 (스케줄러용). */
    public int syncRecentPhotos() {
        String modifiedtime = LocalDate.now().minusDays(DEFAULT_LOOKBACK_DAYS).format(YYYYMMDD);
        return syncPhotos(modifiedtime);
    }

    /**
     * {@code modifiedtime}(yyyyMMdd) 이후 변경된 관광사진을 저장된 갤러리 이미지에 반영한다.
     * 반환값은 반영(갱신·삭제)된 이미지 건수.
     */
    public int syncPhotos(String modifiedtime) {
        TourApiPhotoResponse response;
        try {
            response = tourPhotoClient.syncGalleryDetail(modifiedtime, 1, PAGE_SIZE);
        } catch (FeignException e) {
            log.warn("[동기화] 관광사진 증분 조회 실패 from={} - 건너뜀: {}", modifiedtime, e.getMessage());
            return 0;
        }
        if (response != null && response.isError()) {
            log.warn("[동기화] 관광사진 TourAPI 오류 응답 from={} code={} msg={} - 건너뜀",
                    modifiedtime, response.resultCode(), response.resultMsg());
            return 0;
        }

        List<TourApiPhotoResponse.Item> items = response.allItems();
        Integer applied = transactionTemplate.execute(status -> {
            int a = 0;
            for (TourApiPhotoResponse.Item item : items) {
                a += reconcile(item);
            }
            return a;
        });
        int result = applied != null ? applied : 0;
        log.info("[동기화] 관광사진 {}건 반영", result);
        return result;
    }

    private int reconcile(TourApiPhotoResponse.Item item) {
        String imageUrl = item.galWebImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return 0;
        }
        List<ContentImage> matched =
                contentImageRepository.findBySourceAndImageUrl(ImageSource.PHOTO_GALLERY, imageUrl);
        if (matched.isEmpty()) {
            return 0;
        }

        if (!"1".equals(item.galUseFlag())) {
            contentImageRepository.deleteAll(matched);
            return matched.size();
        }
        for (ContentImage image : matched) {
            image.updateMetadata(item.galTitle(), item.cpyrhtDivCd(), item.galPhotographyMonth());
        }
        return matched.size();
    }
}
