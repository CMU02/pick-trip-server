package travel_agency.pick_trip.domain.share.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryDay;
import travel_agency.pick_trip.domain.itinerary.entity.ItineraryItem;
import travel_agency.pick_trip.domain.itinerary.repository.ItineraryRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.domain.share.dto.response.ShareCreateResponse;
import travel_agency.pick_trip.domain.share.dto.response.SharedItineraryResponse;
import travel_agency.pick_trip.domain.share.entity.ShareToken;
import travel_agency.pick_trip.domain.share.repository.ShareTokenRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShareService")
class ShareServiceTest {

    @Mock private ShareTokenRepository shareTokenRepository;
    @Mock private ItineraryRepository itineraryRepository;
    @InjectMocks private ShareService shareService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ITINERARY_ID = UUID.randomUUID();

    private Itinerary itineraryOwnedBy(UUID ownerId) {
        Itinerary itinerary = Itinerary.builder()
                .userId(ownerId)
                .title("하동 1박 2일")
                .region(Region.HADONG)
                .travelDate(LocalDate.of(2026, 7, 1))
                .duration(2)
                .build();
        ItineraryDay day = ItineraryDay.builder().dayIndex(1).build();
        day.addItem(ItineraryItem.builder()
                .contentId("c1").title("쌍계사").orderIndex(1).reason("오전 배치").pinned(false).build());
        itinerary.addDay(day);
        ReflectionTestUtils.setField(itinerary, "itineraryId", ITINERARY_ID);
        return itinerary;
    }

    @Nested
    @DisplayName("createShare")
    class CreateShare {

        @Test
        @DisplayName("본인 일정이고 활성 토큰이 없으면 새 토큰을 생성한다")
        void noExistingToken_createsNew() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(USER_ID)));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID))
                    .willReturn(Optional.empty());
            given(shareTokenRepository.save(any(ShareToken.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            ShareCreateResponse response = shareService.createShare(USER_ID, ITINERARY_ID);

            assertThat(response.token()).isNotBlank();
            assertThat(response.shareUrl()).isEqualTo("/api/v1/share/" + response.token());
            verify(shareTokenRepository).save(any(ShareToken.class));
        }

        @Test
        @DisplayName("이미 활성 토큰이 있으면 재사용하고 새로 저장하지 않는다")
        void existingToken_reuses() {
            ShareToken existing = ShareToken.builder().itineraryId(ITINERARY_ID).token("existing-token").build();
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(USER_ID)));
            given(shareTokenRepository.findByItineraryIdAndActiveTrue(ITINERARY_ID))
                    .willReturn(Optional.of(existing));

            ShareCreateResponse response = shareService.createShare(USER_ID, ITINERARY_ID);

            assertThat(response.token()).isEqualTo("existing-token");
            verify(shareTokenRepository, never()).save(any(ShareToken.class));
        }

        @Test
        @DisplayName("타인 소유 일정이면 ITINERARY_NOT_FOUND 예외를 던진다")
        void notOwned_throws() {
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(UUID.randomUUID())));

            ThrowingCallable action = () -> shareService.createShare(USER_ID, ITINERARY_ID);

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITINERARY_NOT_FOUND);
            verify(shareTokenRepository, never()).save(any(ShareToken.class));
        }
    }

    @Nested
    @DisplayName("getSharedItinerary")
    class GetSharedItinerary {

        @Test
        @DisplayName("활성 토큰이면 공개 일정을 반환한다")
        void activeToken_returnsPublicView() {
            ShareToken shareToken = ShareToken.builder().itineraryId(ITINERARY_ID).token("tok").build();
            given(shareTokenRepository.findByTokenAndActiveTrue("tok")).willReturn(Optional.of(shareToken));
            given(itineraryRepository.findWithDaysById(ITINERARY_ID))
                    .willReturn(Optional.of(itineraryOwnedBy(USER_ID)));

            SharedItineraryResponse response = shareService.getSharedItinerary("tok");

            assertThat(response.title()).isEqualTo("하동 1박 2일");
            assertThat(response.days().get(0).items().get(0).contentId()).isEqualTo("c1");
            assertThat(response.days().get(0).items().get(0).reason()).isEqualTo("오전 배치");
        }

        @Test
        @DisplayName("토큰이 없거나 비활성이면 SHARE_ITINERARY_NOT_FOUND 예외를 던진다")
        void inactiveOrMissingToken_throws() {
            given(shareTokenRepository.findByTokenAndActiveTrue("tok")).willReturn(Optional.empty());

            ThrowingCallable action = () -> shareService.getSharedItinerary("tok");

            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SHARE_ITINERARY_NOT_FOUND);
        }
    }
}
