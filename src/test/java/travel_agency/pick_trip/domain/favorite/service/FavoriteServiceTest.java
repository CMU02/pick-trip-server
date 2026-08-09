package travel_agency.pick_trip.domain.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteService")
class FavoriteServiceTest {

    @Mock private FavoriteRepository favoriteRepository;
    @InjectMocks private FavoriteService favoriteService;

    private static final UUID USER_ID = UUID.randomUUID();

    private Favorite newFavorite(String contentId) {
        return Favorite.builder()
                .userId(USER_ID)
                .contentId(contentId)
                .title("쌍계사")
                .address("경남 하동군")
                .firstImage("https://img/1.jpg")
                .category(ContentCategory.ATTRACTION)
                .summary("천년 고찰")
                .indoor(false)
                .region(Region.HADONG)
                .build();
    }

    @Nested
    @DisplayName("getFavorites")
    class GetFavorites {

        @Test
        @DisplayName("찜한 콘텐츠가 있으면 최신순으로 정렬된 목록을 반환한다")
        void hasFavorites_returnsResponse() {
            // given
            given(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of(newFavorite("126508")));

            // when
            FavoritesResponse response = favoriteService.getFavorites(USER_ID);

            // then
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).contentId()).isEqualTo("126508");
        }

        @Test
        @DisplayName("찜한 콘텐츠가 없으면 빈 목록을 반환한다")
        void noFavorites_returnsEmptyResponse() {
            // given
            given(favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                    .willReturn(List.of());

            // when
            FavoritesResponse response = favoriteService.getFavorites(USER_ID);

            // then
            assertThat(response.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addFavorite")
    class AddFavorite {

        private final AddFavoriteRequest request = new AddFavoriteRequest(
                "126508", "쌍계사", "경남 하동군", "https://img/1.jpg",
                ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG
        );

        @Test
        @DisplayName("중복이 아니면 찜을 추가한다")
        void noDuplicate_addsFavorite() {
            // given
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "126508")).willReturn(false);
            given(favoriteRepository.saveAndFlush(any(Favorite.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            FavoriteResponse response = favoriteService.addFavorite(USER_ID, request);

            // then
            assertThat(response.contentId()).isEqualTo("126508");
            assertThat(response.title()).isEqualTo("쌍계사");
            assertThat(response.region()).isEqualTo(Region.HADONG);
        }

        @Test
        @DisplayName("이미 찜한 콘텐츠를 추가하면 FAVORITE_DUPLICATE 예외를 던진다")
        void duplicate_throwsException() {
            // given
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "126508")).willReturn(true);

            // when
            ThrowableAssert.ThrowingCallable action = () -> favoriteService.addFavorite(USER_ID, request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAVORITE_DUPLICATE);
            verify(favoriteRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("사전 중복 체크를 통과했지만 동시 요청으로 저장 시 unique constraint를 위반하면 FAVORITE_DUPLICATE 예외를 던진다")
        void concurrentDuplicate_savesThrowsDataIntegrityViolation_throwsFavoriteDuplicateException() {
            // given
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "126508")).willReturn(false);
            given(favoriteRepository.saveAndFlush(any(Favorite.class)))
                    .willThrow(new DataIntegrityViolationException("uk_favorites_user_content violated"));

            // when
            ThrowableAssert.ThrowingCallable action = () -> favoriteService.addFavorite(USER_ID, request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAVORITE_DUPLICATE);
        }

        @Test
        @DisplayName("address가 없어도(null) 찜을 추가한다")
        void nullAddress_addsFavorite() {
            // given
            AddFavoriteRequest requestWithoutAddress = new AddFavoriteRequest(
                    "302000", "지리산 둘레길", null, null,
                    null, null, null, Region.HADONG
            );
            given(favoriteRepository.existsByUserIdAndContentId(USER_ID, "302000")).willReturn(false);
            given(favoriteRepository.saveAndFlush(any(Favorite.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            FavoriteResponse response = favoriteService.addFavorite(USER_ID, requestWithoutAddress);

            // then
            assertThat(response.contentId()).isEqualTo("302000");
            assertThat(response.title()).isEqualTo("지리산 둘레길");
            assertThat(response.address()).isNull();
        }
    }

    @Nested
    @DisplayName("removeFavorite")
    class RemoveFavorite {

        @Test
        @DisplayName("찜한 콘텐츠가 있으면 삭제한다")
        void favoriteExists_removesFavorite() {
            // given
            Favorite favorite = newFavorite("126508");
            given(favoriteRepository.findByUserIdAndContentId(USER_ID, "126508"))
                    .willReturn(Optional.of(favorite));

            // when
            favoriteService.removeFavorite(USER_ID, "126508");

            // then
            verify(favoriteRepository).delete(favorite);
        }

        @Test
        @DisplayName("찜한 콘텐츠가 없으면 FAVORITE_NOT_FOUND 예외를 던진다")
        void favoriteMissing_throwsException() {
            // given
            given(favoriteRepository.findByUserIdAndContentId(USER_ID, "126508"))
                    .willReturn(Optional.empty());

            // when
            ThrowableAssert.ThrowingCallable action = () -> favoriteService.removeFavorite(USER_ID, "126508");

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FAVORITE_NOT_FOUND);
        }
    }
}
