package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourPhotoClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiPhotoResponse;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.ImageSource;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.content.repository.TravelContentRepository;
import travel_agency.pick_trip.domain.region.Region;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageEnrichService")
class ImageEnrichServiceTest {

    @Mock private TourPhotoClient tourPhotoClient;
    @Mock private TravelContentRepository travelContentRepository;

    @InjectMocks private ImageEnrichService imageEnrichService;

    private TravelContent contentWithoutImage() {
        return TravelContent.builder()
                .sourceContentId("126508")
                .title("화개장터")
                .region(Region.HADONG)
                .build();
    }

    private TourApiPhotoResponse photoResponse(String useFlag) {
        return new TourApiPhotoResponse(new TourApiPhotoResponse.Response(
                new TourApiPhotoResponse.Body(new TourApiPhotoResponse.Items(List.of(
                        new TourApiPhotoResponse.Item(
                                "126508", "화개장터 봄", "http://photo/1.jpg", "202604", "Type1", useFlag))),
                        30, 1, 1)));
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-photo 5xx") {};
    }

    @Test
    @DisplayName("이미지가 없는 콘텐츠에 갤러리 이미지를 보강한다")
    void enrichRegion_이미지없음_보강() {
        TravelContent content = contentWithoutImage();
        given(travelContentRepository.findByRegion(Region.HADONG)).willReturn(List.of(content));
        given(tourPhotoClient.searchGallery(anyString(), anyInt(), anyInt())).willReturn(photoResponse("1"));

        int enriched = imageEnrichService.enrichRegion(Region.HADONG);

        assertThat(enriched).isEqualTo(1);
        assertThat(content.getImages()).hasSize(1);
        ContentImage image = content.getImages().get(0);
        assertThat(image.getSource()).isEqualTo(ImageSource.PHOTO_GALLERY);
        assertThat(image.getImageUrl()).isEqualTo("http://photo/1.jpg");
        assertThat(image.getPhotographyMonth()).isEqualTo("202604");
        verify(travelContentRepository).save(content);
    }

    @Test
    @DisplayName("이미 이미지가 있는 콘텐츠는 갤러리를 조회하지 않는다")
    void enrichRegion_이미지있음_건너뜀() {
        TravelContent content = contentWithoutImage();
        content.addImage(ContentImage.builder()
                .source(ImageSource.TOUR_API).imageUrl("http://tour/1.jpg").build());
        given(travelContentRepository.findByRegion(Region.HADONG)).willReturn(List.of(content));

        int enriched = imageEnrichService.enrichRegion(Region.HADONG);

        assertThat(enriched).isZero();
        verify(tourPhotoClient, never()).searchGallery(anyString(), anyInt(), anyInt());
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("갤러리 조회가 실패하면 해당 콘텐츠를 건너뛴다")
    void enrichRegion_조회실패_건너뜀() {
        TravelContent content = contentWithoutImage();
        given(travelContentRepository.findByRegion(Region.HADONG)).willReturn(List.of(content));
        given(tourPhotoClient.searchGallery(anyString(), anyInt(), anyInt())).willThrow(feignError());

        int enriched = imageEnrichService.enrichRegion(Region.HADONG);

        assertThat(enriched).isZero();
        assertThat(content.getImages()).isEmpty();
        verify(travelContentRepository, never()).save(any());
    }

    @Test
    @DisplayName("갤러리 응답이 오류 결과코드(HTTP 200)면 해당 콘텐츠를 건너뛴다")
    void enrichRegion_오류코드_건너뜀() {
        TravelContent content = contentWithoutImage();
        given(travelContentRepository.findByRegion(Region.HADONG)).willReturn(List.of(content));
        given(tourPhotoClient.searchGallery(anyString(), anyInt(), anyInt())).willReturn(errorResponse());

        int enriched = imageEnrichService.enrichRegion(Region.HADONG);

        assertThat(enriched).isZero();
        assertThat(content.getImages()).isEmpty();
        verify(travelContentRepository, never()).save(any());
    }

    private TourApiPhotoResponse errorResponse() {
        return new TourApiPhotoResponse(new TourApiPhotoResponse.Response(
                new TourApiPhotoResponse.Header("22", "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"),
                new TourApiPhotoResponse.Body(new TourApiPhotoResponse.Items(List.of()), 0, 1, 0)));
    }
}
