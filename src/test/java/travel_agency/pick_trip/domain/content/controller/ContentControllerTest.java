package travel_agency.pick_trip.domain.content.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.content.service.ContentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentController")
class ContentControllerTest {

    @Mock private ContentService contentService;
    @InjectMocks private ContentController contentController;

    @Nested
    @DisplayName("GET /api/v1/contents")
    class GetContents {

        @Test
        @DisplayName("정상 요청이면 200과 ContentListResponse를 반환한다")
        void validRequest_returns200WithList() {
            // given
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58)
            ));
            given(contentService.getContents(any(ContentListRequest.class))).willReturn(expected);

            // when
            ResponseEntity<ContentListResponse> result = contentController.getContents(
                    "HADONG", null, null, null, null, 0, 20
            );

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}")
    class GetContentDetail {

        @Test
        @DisplayName("정상 요청이면 200과 ContentDetailResponse를 반환한다")
        void validContentId_returns200WithDetail() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가",
                    "약 2시간", null, "TourAPI", List.of()
            );
            given(contentService.getContentDetail("2741429")).willReturn(expected);

            // when
            ResponseEntity<ContentDetailResponse> result = contentController.getContentDetail("2741429");

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().contentId()).isEqualTo("2741429");
        }
    }
}
