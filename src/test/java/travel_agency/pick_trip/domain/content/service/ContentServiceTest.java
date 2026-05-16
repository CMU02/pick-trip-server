package travel_agency.pick_trip.domain.content.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.adapter.TourApiContentAdapter;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentDetailResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.content.dto.response.ContentSummaryResponse;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService")
class ContentServiceTest {

    @Mock private TourApiContentAdapter adapter;
    @InjectMocks private ContentService contentService;

    @Nested
    @DisplayName("getContents")
    class GetContents {

        @Test
        @DisplayName("유효한 region으로 요청하면 ContentListResponse를 반환한다")
        void validRegion_returnsContentListResponse() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, 0, 20);
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of(
                    new ContentSummaryResponse("123", "쌍계사", 12, "경상남도 하동군", "https://img.jpg", 35.27, 127.58)
            ));
            given(adapter.fetchList(request, Region.HADONG)).willReturn(expected);

            // when
            ContentListResponse result = contentService.getContents(request);

            // then
            assertThat(result.totalCount()).isEqualTo(1);
            assertThat(result.items().get(0).title()).isEqualTo("쌍계사");
        }

        @Test
        @DisplayName("지원하지 않는 region이면 CONTENT_INVALID_REGION 예외를 던진다")
        void invalidRegion_throwsContentInvalidRegion() {
            // given
            ContentListRequest request = new ContentListRequest("INVALID", null, null, 0, 20);

            // when & then
            assertThatThrownBy(() -> contentService.getContents(request))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_INVALID_REGION);
        }
    }

    @Nested
    @DisplayName("getContentDetail")
    class GetContentDetail {

        @Test
        @DisplayName("유효한 contentId로 상세 조회 시 ContentDetailResponse를 반환한다")
        void validContentId_returnsContentDetailResponse() {
            // given
            ContentDetailResponse expected = new ContentDetailResponse(
                    "2741429", "쌍계사", 12, "경상남도 하동군", "055-883-1901", "http://ssanggyesa.net",
                    35.27, 127.58, "한국의 4대 총림", "03:00~18:00", "연중무휴",
                    "가능", "성인 3,000원", "불가", "불가", List.of()
            );
            given(adapter.fetchDetail("2741429")).willReturn(expected);

            // when
            ContentDetailResponse result = contentService.getContentDetail("2741429");

            // then
            assertThat(result.contentId()).isEqualTo("2741429");
            assertThat(result.title()).isEqualTo("쌍계사");
        }
    }
}
