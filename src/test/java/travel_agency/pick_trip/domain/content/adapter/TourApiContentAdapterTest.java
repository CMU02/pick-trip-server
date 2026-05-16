package travel_agency.pick_trip.domain.content.adapter;

import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import travel_agency.pick_trip.domain.content.client.TourApiClient;
import travel_agency.pick_trip.domain.content.client.dto.TourApiListResponse;
import travel_agency.pick_trip.domain.content.dto.request.ContentListRequest;
import travel_agency.pick_trip.domain.content.dto.response.ContentListResponse;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("TourApiContentAdapter")
class TourApiContentAdapterTest {

    @Mock private TourApiClient tourApiClient;
    @Mock private TourApiContentMapper mapper;
    @InjectMocks private TourApiContentAdapter adapter;

    @Nested
    @DisplayName("fetchList - 키워드 없음")
    class FetchListWithoutKeyword {

        @Test
        @DisplayName("keyword가 없으면 areaBasedList2를 호출한다")
        void noKeyword_callsAreaBasedList() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, null, 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(0, 0, 20, List.of());

            given(tourApiClient.getAreaBasedList(
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchList - 키워드 있음")
    class FetchListWithKeyword {

        @Test
        @DisplayName("keyword가 있으면 searchKeyword2를 호출한다")
        void withKeyword_callsSearchKeyword() {
            // given
            ContentListRequest request = new ContentListRequest("HADONG", null, "쌍계사", 0, 20);
            Region region = Region.HADONG;
            TourApiListResponse rawResponse = emptyListResponse();
            ContentListResponse expected = new ContentListResponse(1, 0, 20, List.of());

            given(tourApiClient.searchByKeyword(
                    eq("쌍계사"),
                    eq(region.getAreaCode()),
                    eq(region.getSigunguCode()),
                    isNull(),
                    eq(1),
                    eq(20)
            )).willReturn(rawResponse);
            given(mapper.toListResponse(rawResponse, 0, 20)).willReturn(expected);

            // when
            ContentListResponse result = adapter.fetchList(request, region);

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("fetchDetail")
    class FetchDetail {

        @Test
        @DisplayName("TourAPI 호출 실패 시 CONTENT_PROVIDER_FAILED 예외를 던진다")
        void feignException_throwsContentProviderFailed() {
            // given
            given(tourApiClient.getDetailCommon(any()))
                    .willThrow(FeignException.class);

            // when & then
            assertThatThrownBy(() -> adapter.fetchDetail("2741429"))
                    .isInstanceOf(ContentException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CONTENT_PROVIDER_FAILED);
        }
    }

    private TourApiListResponse emptyListResponse() {
        return new TourApiListResponse(
                new TourApiListResponse.Response(
                        new TourApiListResponse.Body(
                                new TourApiListResponse.Items(List.of()),
                                20, 1, 0
                        )
                )
        );
    }
}
