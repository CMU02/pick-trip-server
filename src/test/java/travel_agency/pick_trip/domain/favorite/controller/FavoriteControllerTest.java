package travel_agency.pick_trip.domain.favorite.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.service.FavoriteService;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoriteController")
class FavoriteControllerTest {

    @Mock private FavoriteService favoriteService;
    @InjectMocks private FavoriteController favoriteController;

    private static final UUID USER_UID = UUID.randomUUID();

    // standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    private FavoriteResponse favoriteResponse(String contentId) {
        return new FavoriteResponse(
                UUID.randomUUID(), contentId, "쌍계사", "경남 하동군", "https://img/1.jpg",
                ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("GET /api/v1/favorites")
    class GetFavorites {

        @Test
        @DisplayName("인증된 사용자가 요청하면 200과 찜 목록을 반환한다")
        void authenticated_returns200WithFavorites() {
            // given
            FavoritesResponse expected = new FavoritesResponse(List.of(favoriteResponse("126508")));
            given(favoriteService.getFavorites(USER_UID)).willReturn(expected);

            // when
            ResponseEntity<FavoritesResponse> result = favoriteController.getFavorites(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/favorites")
    class AddFavorite {

        @Test
        @DisplayName("콘텐츠를 찜하면 201과 추가된 항목을 반환한다")
        void addFavorite_returns201() {
            // given
            AddFavoriteRequest request = new AddFavoriteRequest(
                    "126508", "쌍계사", "경남 하동군", "https://img/1.jpg",
                    ContentCategory.ATTRACTION, "천년 고찰", false, Region.HADONG
            );
            given(favoriteService.addFavorite(USER_UID, request)).willReturn(favoriteResponse("126508"));

            // when
            ResponseEntity<FavoriteResponse> result = favoriteController.addFavorite(principal(), request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().contentId()).isEqualTo("126508");
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/favorites/{contentId}")
    class RemoveFavorite {

        @Test
        @DisplayName("찜을 해제하면 204를 반환하고 서비스에 위임한다")
        void removeFavorite_returns204() {
            // when
            ResponseEntity<Void> result = favoriteController.removeFavorite(principal(), "126508");

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(favoriteService).removeFavorite(USER_UID, "126508");
        }
    }
}
