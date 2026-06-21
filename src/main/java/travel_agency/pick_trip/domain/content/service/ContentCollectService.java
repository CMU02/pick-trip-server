package travel_agency.pick_trip.domain.content.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.entity.ContentDetail;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 콘텐츠 수집 (수집 4단계). {@code /areaBasedList2}로 후보를 모으고
 * {@code /detailCommon2}·{@code /detailIntro2}·{@code /detailImage2}로 보강해
 * {@link TravelContent}/{@link ContentDetail}/{@link ContentImage}에 upsert한다.
 *
 * <p>보조 캐시 적재이며 실시간 조회 흐름은 변경하지 않는다. 콘텐츠 단위로 {@link FeignException}을
 * 잡아 일부 실패가 전체 수집을 막지 않게 하고, 실패 항목은 건너뛰며 로그를 남긴다(수동 트리거 전제).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCollectService {

    /** MVP 콘텐츠 타입: 관광지·문화시설·축제·여행코스·레포츠·쇼핑·음식 */
    private static final List<String> MVP_CONTENT_TYPE_IDS =
            List.of("12", "14", "15", "25", "28", "38", "39");
    private static final int PAGE_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TravelContentRepository travelContentRepository;
    private final ContentCollectMapper mapper;

    /** 지역의 MVP 콘텐츠 타입을 모두 수집한다. 반환값은 성공 적재 건수. */
    @Transactional
    public int collectRegion(Region region) {
        int collected = 0;
        for (String contentTypeId : MVP_CONTENT_TYPE_IDS) {
            collected += collectRegionByType(region, contentTypeId);
        }
        log.info("[수집] {} 지역 콘텐츠 {}건 적재", region, collected);
        return collected;
    }

    private int collectRegionByType(Region region, String contentTypeId) {
        List<TourApiListResponse.Item> items;
        try {
            items = mapper.listItems(tourApiClient.getAreaBasedList(
                    region.getAreaCode(), region.getSigunguCode(), contentTypeId, 1, PAGE_SIZE));
        } catch (FeignException e) {
            log.warn("[수집] 목록 조회 실패 region={} type={} - 건너뜀: {}", region, contentTypeId, e.getMessage());
            return 0;
        }

        int count = 0;
        for (TourApiListResponse.Item item : items) {
            if (upsertContent(region, item)) {
                count++;
            }
        }
        return count;
    }

    private boolean upsertContent(Region region, TourApiListResponse.Item listItem) {
        String contentId = listItem.contentid();
        if (contentId == null || contentId.isBlank()) {
            return false;
        }
        try {
            TravelContent content = travelContentRepository.findById(contentId)
                    .orElseGet(() -> newContent(region, listItem));

            enrichCommon(content, contentId);
            enrichIntro(content, contentId);
            enrichImages(content, contentId);

            travelContentRepository.save(content);
            return true;
        } catch (FeignException e) {
            log.warn("[수집] 콘텐츠 {} 상세 보강 실패 - 건너뜀: {}", contentId, e.getMessage());
            return false;
        }
    }

    private TravelContent newContent(Region region, TourApiListResponse.Item listItem) {
        return TravelContent.builder()
                .sourceContentId(listItem.contentid())
                .contentTypeId(listItem.contenttypeid())
                .title(listItem.title())
                .region(region)
                .latitude(mapper.parseLatitude(listItem.mapy()))
                .longitude(mapper.parseLongitude(listItem.mapx()))
                .firstImage(listItem.firstimage())
                .dataStatus(DataStatus.ACTIVE)
                .build();
    }

    private void enrichCommon(TravelContent content, String contentId) {
        TourApiDetailCommonResponse.Item common = mapper.firstCommon(tourApiClient.getDetailCommon(contentId));
        if (common == null) {
            return;
        }
        content.updateSourceData(
                common.contenttypeid(),
                common.title(),
                null,
                common.overview(),
                mapper.buildAddress(common.addr1(), common.addr2()),
                mapper.parseLatitude(common.mapy()),
                mapper.parseLongitude(common.mapx()),
                common.tel(),
                common.homepage(),
                common.firstimage(),
                null
        );
    }

    private void enrichIntro(TravelContent content, String contentId) {
        String contentTypeId = content.getContentTypeId();
        if (contentTypeId == null || contentTypeId.isBlank()) {
            return;
        }
        TourApiDetailIntroResponse.Item intro =
                mapper.firstIntro(tourApiClient.getDetailIntro(contentId, contentTypeId));
        if (intro == null) {
            return;
        }
        ContentDetail detail = content.getDetail();
        if (detail == null) {
            detail = ContentDetail.builder().build();
            content.assignDetail(detail);
        }
        detail.updateIntro(
                intro.usetime(),
                intro.restdate(),
                intro.parking(),
                intro.chkbabycarriage(),
                intro.chkpet(),
                intro.usefee()
        );
    }

    private void enrichImages(TravelContent content, String contentId) {
        List<ContentImage> images = mapper.toContentImages(tourApiClient.getDetailImage(contentId));
        if (!images.isEmpty()) {
            content.replaceImages(images);
        }
    }
}
