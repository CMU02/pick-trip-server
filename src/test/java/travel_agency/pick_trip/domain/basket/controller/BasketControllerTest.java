package travel_agency.pick_trip.domain.basket.controller;

import io.jsonwebtoken.Claims;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
import travel_agency.pick_trip.domain.basket.dto.request.AddBasketItemRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketConditionsRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketItemPriorityRequest;
import travel_agency.pick_trip.domain.basket.dto.response.BasketItemResponse;
import travel_agency.pick_trip.domain.basket.dto.response.BasketResponse;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.basket.service.BasketService;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.jwt.JwtUserPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BasketController")
class BasketControllerTest {

    @Mock private BasketService basketService;
    @InjectMocks private BasketController basketController;

    private static final UUID USER_UID = UUID.randomUUID();

    // standaloneSetup에서 @AuthenticationPrincipal 주입이 불안정하므로 컨트롤러를 직접 호출한다
    private JwtUserPrincipal principal() {
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn(USER_UID.toString());
        given(claims.get("role", String.class)).willReturn("USER");
        return JwtUserPrincipal.from(claims);
    }

    private BasketItemResponse itemResponse(UUID itemId, Priority priority) {
        return new BasketItemResponse(itemId, "2741429", "쌍계사", "https://img.jpg", "12", priority);
    }

    @Nested
    @DisplayName("GET /api/v1/baskets")
    class GetBasket {

        @Test
        @DisplayName("인증된 사용자가 요청하면 200과 바구니를 반환한다")
        void authenticated_returns200WithBasket() {
            // given
            BasketResponse expected = new BasketResponse(
                    UUID.randomUUID(),
                    new BasketResponse.Conditions(Region.HADONG, LocalDate.of(2026, 7, 1), 2,
                            Set.of(TravelCondition.WITH_CHILD)),
                    List.of(itemResponse(UUID.randomUUID(), Priority.MUST_VISIT))
            );
            given(basketService.getBasket(USER_UID)).willReturn(expected);

            // when
            ResponseEntity<BasketResponse> result = basketController.getBasket(principal());

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().items()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/baskets/conditions")
    class UpdateConditions {

        @Test
        @DisplayName("여행 조건을 갱신하면 200과 갱신된 바구니를 반환한다")
        void updatesConditions_returns200() {
            // given
            UpdateBasketConditionsRequest request = new UpdateBasketConditionsRequest(
                    Region.YEONGJU, LocalDate.of(2026, 8, 1), 3, Set.of(TravelCondition.WITH_PARENTS));
            BasketResponse expected = new BasketResponse(
                    UUID.randomUUID(),
                    new BasketResponse.Conditions(Region.YEONGJU, LocalDate.of(2026, 8, 1), 3,
                            Set.of(TravelCondition.WITH_PARENTS)),
                    List.of()
            );
            given(basketService.updateConditions(USER_UID, request)).willReturn(expected);

            // when
            ResponseEntity<BasketResponse> result = basketController.updateConditions(principal(), request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().conditions().region()).isEqualTo(Region.YEONGJU);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/baskets/items")
    class AddItem {

        @Test
        @DisplayName("콘텐츠를 추가하면 201과 추가된 항목을 반환한다")
        void addItem_returns201() {
            // given
            AddBasketItemRequest request = new AddBasketItemRequest(
                    "2741429", Priority.MUST_VISIT, "쌍계사", "https://img.jpg", "12");
            UUID itemId = UUID.randomUUID();
            given(basketService.addItem(USER_UID, request)).willReturn(itemResponse(itemId, Priority.MUST_VISIT));

            // when
            ResponseEntity<BasketItemResponse> result = basketController.addItem(principal(), request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().itemId()).isEqualTo(itemId);
            assertThat(result.getBody().priority()).isEqualTo(Priority.MUST_VISIT);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/baskets/items/{itemId}")
    class ChangePriority {

        @Test
        @DisplayName("우선순위를 변경하면 200과 변경된 항목을 반환한다")
        void changePriority_returns200() {
            // given
            UUID itemId = UUID.randomUUID();
            UpdateBasketItemPriorityRequest request = new UpdateBasketItemPriorityRequest(Priority.OPTIONAL);
            given(basketService.changePriority(USER_UID, itemId, request))
                    .willReturn(itemResponse(itemId, Priority.OPTIONAL));

            // when
            ResponseEntity<BasketItemResponse> result = basketController.changePriority(principal(), itemId, request);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().priority()).isEqualTo(Priority.OPTIONAL);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/baskets/items/{itemId}")
    class RemoveItem {

        @Test
        @DisplayName("항목을 삭제하면 204를 반환하고 서비스에 위임한다")
        void removeItem_returns204() {
            // given
            UUID itemId = UUID.randomUUID();

            // when
            ResponseEntity<Void> result = basketController.removeItem(principal(), itemId);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(basketService).removeItem(USER_UID, itemId);
        }
    }
}
