package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailCommonResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailImageResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiDetailIntroResponse;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentCollectService")
class ContentCollectServiceTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TravelContentRepository travelContentRepository;

    private ContentCollectService contentCollectService;

    private static final Region REGION = Region.HADONG; // areaCode=36, sigunguCode=18
    private static final String CONTENT_ID = "126508";

    @BeforeEach
    void setUp() {
        // ContentCollectMapper 는 순수 파싱 로직이므로 실제 인스턴스를 사용한다.
        contentCollectService = new ContentCollectService(
                tourApiClient, travelContentRepository, new ContentCollectMapper());
    }

    // --- 테스트 헬퍼 ---

    private TourApiListResponse listResponse(TourApiListResponse.Item... items) {
        return new TourApiListResponse(new TourApiListResponse.Response(
                new TourApiListResponse.Body(new TourApiListResponse.Items(List.of(items)), 100, 1, items.length)));
    }

    private TourApiListResponse.Item listItem() {
        return new TourApiListResponse.Item(
                CONTENT_ID, "12", "화개장터", "경남 하동군", "화개면", "127.7", "35.1", "list.jpg", "list2.jpg");
    }

    private TourApiDetailCommonResponse commonResponse() {
        return new TourApiDetailCommonResponse(new TourApiDetailCommonResponse.Response(
                new TourApiDetailCommonResponse.Body(new TourApiDetailCommonResponse.Items(List.of(
                        new TourApiDetailCommonResponse.Item(
                                CONTENT_ID, "12", "화개장터", "경남 하동군 화개면", "탑리", "055-000-0000",
                                "http://hwagae.kr", "127.7", "35.1", "common.jpg", "지리산 자락의 전통 장터"))))));
    }

    private TourApiDetailIntroResponse introResponse() {
        return new TourApiDetailIntroResponse(new TourApiDetailIntroResponse.Response(
                new TourApiDetailIntroResponse.Body(new TourApiDetailIntroResponse.Items(List.of(
                        new TourApiDetailIntroResponse.Item(
                                CONTENT_ID, "12", "09:00~18:00", "연중무휴", "가능", "무료", "가능", "불가"))))));
    }

    private TourApiDetailImageResponse imageResponse() {
        return new TourApiDetailImageResponse(new TourApiDetailImageResponse.Response(
                new TourApiDetailImageResponse.Body(new TourApiDetailImageResponse.Items(List.of(
                        new TourApiDetailImageResponse.Item(CONTENT_ID, "detail1.jpg", "전경"))))));
    }

    private void stubDetailCalls() {
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willReturn(commonResponse());
        given(tourApiClient.getDetailIntro(CONTENT_ID, "12")).willReturn(introResponse());
        given(tourApiClient.getDetailImage(CONTENT_ID)).willReturn(imageResponse());
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-api 5xx") {};
    }

    @Test
    @DisplayName("신규 콘텐츠를 상세 보강과 함께 저장한다")
    void collectRegion_신규콘텐츠_저장() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        stubDetailCalls();

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isEqualTo(1);

        ArgumentCaptor<TravelContent> captor = ArgumentCaptor.forClass(TravelContent.class);
        verify(travelContentRepository, times(1)).save(captor.capture());

        TravelContent saved = captor.getValue();
        assertThat(saved.getSourceContentId()).isEqualTo(CONTENT_ID);
        assertThat(saved.getRegion()).isEqualTo(Region.HADONG);
        assertThat(saved.getSummary()).isEqualTo("지리산 자락의 전통 장터");
        assertThat(saved.getAddress()).isEqualTo("경남 하동군 화개면 탑리");
        assertThat(saved.getDetail()).isNotNull();
        assertThat(saved.getDetail().getUseTime()).isEqualTo("09:00~18:00");
        assertThat(saved.getImages()).hasSize(1);
        assertThat(saved.getImages().get(0).getImageUrl()).isEqualTo("detail1.jpg");
    }

    @Test
    @DisplayName("기존 콘텐츠는 상세 정보를 갱신한다")
    void collectRegion_기존콘텐츠_갱신() {
        TravelContent existing = TravelContent.builder()
                .sourceContentId(CONTENT_ID)
                .contentTypeId("12")
                .title("옛 제목")
                .region(Region.HADONG)
                .build();
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.of(existing));
        stubDetailCalls();

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isEqualTo(1);
        assertThat(existing.getTitle()).isEqualTo("화개장터");
        assertThat(existing.getDetail()).isNotNull();
        verify(travelContentRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("상세 보강이 실패하면 해당 콘텐츠를 건너뛰고 저장하지 않는다")
    void collectRegion_상세실패_건너뜀() {
        given(tourApiClient.getAreaBasedList("36", "18", "12", 1, 100))
                .willReturn(listResponse(listItem()));
        given(travelContentRepository.findById(CONTENT_ID)).willReturn(Optional.empty());
        given(tourApiClient.getDetailCommon(CONTENT_ID)).willThrow(feignError());

        int collected = contentCollectService.collectRegion(REGION);

        assertThat(collected).isZero();
        verify(travelContentRepository, never()).save(any());
    }
}
