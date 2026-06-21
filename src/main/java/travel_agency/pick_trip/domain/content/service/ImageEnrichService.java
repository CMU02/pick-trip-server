package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageEnrichService {

    private static final int PAGE_SIZE = 30;

    private final TourPhotoClient tourPhotoClient;
    private final TravelContentRepository travelContentRepository;

    /** 지역에서 이미지가 없는 콘텐츠에 갤러리 이미지를 보강한다. 반환값은 보강 건수. */
    @Transactional
    public int enrichRegion(Region region) {
        int enriched = 0;
        for (TravelContent content : travelContentRepository.findByRegion(region)) {
            if (!content.getImages().isEmpty()) {
                continue; // 이미 이미지가 있으면 보강하지 않는다.
            }
            if (enrichOne(content)) {
                enriched++;
            }
        }
        log.info("[보강] {} 지역 이미지 {}건 보강", region, enriched);
        return enriched;
    }

    private boolean enrichOne(TravelContent content) {
        try {
            TourApiPhotoResponse response = tourPhotoClient.searchGallery(content.getTitle(), 1, PAGE_SIZE);
            if (response != null && response.isError()) {
                log.warn("[보강] 콘텐츠 {} 갤러리 TourAPI 오류 응답 code={} msg={} - 건너뜀",
                        content.getSourceContentId(), response.resultCode(), response.resultMsg());
                return false;
            }
            List<TourApiPhotoResponse.Item> photos = response.usablePhotos();
            if (photos.isEmpty()) {
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
        } catch (FeignException e) {
            log.warn("[보강] 콘텐츠 {} 이미지 보강 실패 - 건너뜀: {}",
                    content.getSourceContentId(), e.getMessage());
            return false;
        }
    }
}
