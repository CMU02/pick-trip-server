package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import travel_agency.pick_trip.domain.content.client.TourPhotoClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiPhotoResponse;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.ImageSource;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 이미지 보강 (수집 6단계). {@code /detailImage2}로 이미지가 채워지지 않은 콘텐츠에 대해
 * 관광사진 갤러리({@code /gallerySearchList1})에서 보조 이미지({@link ImageSource#PHOTO_GALLERY})를 보강한다.
 *
 * <p>콘텐츠 단위로 {@link FeignException}을 잡아 일부 실패가 전체 보강을 막지 않게 한다.
 *
 * <p>보강 대상의 식별자·제목만 짧은 트랜잭션으로 먼저 조회하고, 외부 갤러리 검색은 트랜잭션 밖에서
 * 수행한 뒤, 저장만 다시 짧은 트랜잭션으로 감싼다. 외부 응답 대기 동안 DB 커넥션을 점유하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEnrichService {

    private static final int PAGE_SIZE = 30;

    private final TourPhotoClient tourPhotoClient;
    private final TravelContentRepository travelContentRepository;
    private final TransactionTemplate transactionTemplate;

    /** 보강 대상 식별자·제목(외부 갤러리 검색용). */
    private record EnrichTarget(String contentId, String title) {}

    /** 지역에서 이미지가 없는 콘텐츠에 갤러리 이미지를 보강한다. 반환값은 보강 건수. */
    public int enrichRegion(Region region) {
        // 이미지가 없는 콘텐츠의 (id, title)만 짧은 트랜잭션으로 조회한다(외부 호출 없음).
        List<EnrichTarget> targets = transactionTemplate.execute(status ->
                travelContentRepository.findByRegion(region).stream()
                        .filter(content -> content.getImages().isEmpty())
                        .map(content -> new EnrichTarget(content.getSourceContentId(), content.getTitle()))
                        .toList());
        if (targets == null) {
            targets = List.of();
        }

        int enriched = 0;
        for (EnrichTarget target : targets) {
            if (enrichOne(target)) {
                enriched++;
            }
        }
        log.info("[보강] {} 지역 이미지 {}건 보강", region, enriched);
        return enriched;
    }

    private boolean enrichOne(EnrichTarget target) {
        try {
            // 외부 갤러리 검색은 트랜잭션 밖에서 수행한다.
            TourApiPhotoResponse response = tourPhotoClient.searchGallery(target.title(), 1, PAGE_SIZE);
            if (response == null) {
                return false;
            }
            if (response.isError()) {
                log.warn("[보강] 콘텐츠 {} 갤러리 TourAPI 오류 응답 code={} msg={} - 건너뜀",
                        target.contentId(), response.resultCode(), response.resultMsg());
                return false;
            }
            List<TourApiPhotoResponse.Item> photos = response.usablePhotos();
            if (photos.isEmpty()) {
                return false;
            }
            // 저장만 짧은 트랜잭션으로 감싼다.
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                TravelContent content = travelContentRepository.findById(target.contentId()).orElse(null);
                if (content == null) {
                    return false;
                }
                for (TourApiPhotoResponse.Item photo : photos) {
                    content.addImage(ContentImage.builder()
                            .source(ImageSource.PHOTO_GALLERY)
                            .imageUrl(photo.galWebImageUrl())
                            .title(photo.galTitle())
                            .copyrightType(photo.cpyrhtDivCd())
                            .photographyMonth(photo.galPhotographyMonth())
                            .build());
                }
                travelContentRepository.save(content);
                return true;
            }));
        } catch (FeignException e) {
            log.warn("[보강] 콘텐츠 {} 이미지 보강 실패 - 건너뜀: {}",
                    target.contentId(), e.getMessage());
            return false;
        }
    }
}
