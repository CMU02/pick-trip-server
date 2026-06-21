package travel_agency.pick_trip.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import travel_agency.pick_trip.domain.content.repository.ContentImageRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PhotoSyncService")
class PhotoSyncServiceTest {

    @Mock private TourPhotoClient tourPhotoClient;
    @Mock private ContentImageRepository contentImageRepository;

    @InjectMocks private PhotoSyncService photoSyncService;

    private static final String FROM = "20260614";
    private static final String IMAGE_URL = "http://photo/1.jpg";

    private TourApiPhotoResponse photoResponse(TourApiPhotoResponse.Item... items) {
        return new TourApiPhotoResponse(new TourApiPhotoResponse.Response(
                new TourApiPhotoResponse.Body(new TourApiPhotoResponse.Items(List.of(items)), 100, 1, items.length)));
    }

    private TourApiPhotoResponse.Item photoItem(String url, String useFlag) {
        return new TourApiPhotoResponse.Item("gal-1", "갱신된 제목", url, "202605", "Type2", useFlag);
    }

    private ContentImage galleryImage(String url) {
        return ContentImage.builder()
                .source(ImageSource.PHOTO_GALLERY)
                .imageUrl(url)
                .title("이전 제목")
                .copyrightType("Type1")
                .photographyMonth("202604")
                .build();
    }

    private FeignException feignError() {
        return new FeignException(500, "tour-photo 5xx") {};
    }

    @Test
    @DisplayName("사용 가능한 변경 사진은 매칭 이미지의 메타데이터를 갱신한다")
    void syncPhotos_사용가능_메타갱신() {
        ContentImage image = galleryImage(IMAGE_URL);
        given(tourPhotoClient.syncGalleryDetail(FROM, 1, 100))
                .willReturn(photoResponse(photoItem(IMAGE_URL, "1")));
        given(contentImageRepository.findBySourceAndImageUrl(ImageSource.PHOTO_GALLERY, IMAGE_URL))
                .willReturn(List.of(image));

        int applied = photoSyncService.syncPhotos(FROM);

        assertThat(applied).isEqualTo(1);
        assertThat(image.getTitle()).isEqualTo("갱신된 제목");
        assertThat(image.getCopyrightType()).isEqualTo("Type2");
        assertThat(image.getPhotographyMonth()).isEqualTo("202605");
        verify(contentImageRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("galUseFlag가 1이 아니면 매칭 이미지를 삭제한다")
    void syncPhotos_사용불가_삭제() {
        ContentImage image = galleryImage(IMAGE_URL);
        given(tourPhotoClient.syncGalleryDetail(FROM, 1, 100))
                .willReturn(photoResponse(photoItem(IMAGE_URL, "0")));
        given(contentImageRepository.findBySourceAndImageUrl(ImageSource.PHOTO_GALLERY, IMAGE_URL))
                .willReturn(List.of(image));

        int applied = photoSyncService.syncPhotos(FROM);

        assertThat(applied).isEqualTo(1);
        verify(contentImageRepository).deleteAll(List.of(image));
    }

    @Test
    @DisplayName("매칭되는 저장 이미지가 없으면 건너뛴다")
    void syncPhotos_미매칭_건너뜀() {
        given(tourPhotoClient.syncGalleryDetail(FROM, 1, 100))
                .willReturn(photoResponse(photoItem(IMAGE_URL, "1")));
        given(contentImageRepository.findBySourceAndImageUrl(ImageSource.PHOTO_GALLERY, IMAGE_URL))
                .willReturn(List.of());

        int applied = photoSyncService.syncPhotos(FROM);

        assertThat(applied).isZero();
        verify(contentImageRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("증분 조회가 실패하면 예외를 전파하지 않는다")
    void syncPhotos_조회실패_미반영() {
        given(tourPhotoClient.syncGalleryDetail(eq(FROM), anyInt(), anyInt())).willThrow(feignError());

        int applied = photoSyncService.syncPhotos(FROM);

        assertThat(applied).isZero();
        verify(contentImageRepository, never()).findBySourceAndImageUrl(any(), anyString());
        verify(contentImageRepository, never()).deleteAll(any());
    }
}
