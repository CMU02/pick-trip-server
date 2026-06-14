package travel_agency.pick_trip.domain.basket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import travel_agency.pick_trip.domain.basket.dto.request.AddBasketItemRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketConditionsRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketItemPriorityRequest;
import travel_agency.pick_trip.domain.basket.dto.response.BasketItemResponse;
import travel_agency.pick_trip.domain.basket.dto.response.BasketResponse;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.domain.region.Region;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.PickTripException;

@ExtendWith(MockitoExtension.class)
@DisplayName("BasketService")
class BasketServiceTest {

    @Mock private BasketRepository basketRepository;
    @InjectMocks private BasketService basketService;

    private static final UUID USER_ID = UUID.randomUUID();

    // --- 테스트 헬퍼 ---

    private Basket newBasket() {
        return Basket.builder().userId(USER_ID).build();
    }

    private BasketItem itemWithId(UUID itemId, String contentId, Priority priority) {
        BasketItem item = BasketItem.builder()
                .contentId(contentId)
                .title("title-" + contentId)
                .priority(priority)
                .build();
        ReflectionTestUtils.setField(item, "itemId", itemId);
        return item;
    }

    @Nested
    @DisplayName("getBasket")
    class GetBasket {

        @Test
        @DisplayName("바구니가 있으면 조건과 항목을 담은 BasketResponse를 반환한다")
        void existingBasket_returnsResponse() {
            // given
            Basket basket = newBasket();
            basket.addItem(itemWithId(UUID.randomUUID(), "126508", Priority.MUST_VISIT));
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            BasketResponse response = basketService.getBasket(USER_ID);

            // then
            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).contentId()).isEqualTo("126508");
            assertThat(response.items().get(0).priority()).isEqualTo(Priority.MUST_VISIT);
        }

        @Test
        @DisplayName("바구니가 없으면 빈 BasketResponse를 반환한다")
        void noBasket_returnsEmptyResponse() {
            // given
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when
            BasketResponse response = basketService.getBasket(USER_ID);

            // then
            assertThat(response.basketId()).isNull();
            assertThat(response.items()).isEmpty();
            assertThat(response.conditions().companions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateConditions")
    class UpdateConditions {

        private final UpdateBasketConditionsRequest request = new UpdateBasketConditionsRequest(
                Region.HADONG,
                LocalDate.of(2026, 7, 1),
                2,
                Set.of(TravelCondition.WITH_CHILD, TravelCondition.FOOD_FOCUSED)
        );

        @Test
        @DisplayName("바구니가 있으면 여행 조건을 갱신한다")
        void existingBasket_updatesConditions() {
            // given
            Basket basket = newBasket();
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            BasketResponse response = basketService.updateConditions(USER_ID, request);

            // then
            assertThat(response.conditions().region()).isEqualTo(Region.HADONG);
            assertThat(response.conditions().travelDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(response.conditions().duration()).isEqualTo(2);
            assertThat(response.conditions().companions())
                    .containsExactlyInAnyOrder(TravelCondition.WITH_CHILD, TravelCondition.FOOD_FOCUSED);
            verify(basketRepository, never()).save(any());
        }

        @Test
        @DisplayName("바구니가 없으면 새로 생성한 뒤 여행 조건을 저장한다")
        void noBasket_createsAndSaves() {
            // given
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
            given(basketRepository.save(any(Basket.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            BasketResponse response = basketService.updateConditions(USER_ID, request);

            // then
            assertThat(response.conditions().region()).isEqualTo(Region.HADONG);
            verify(basketRepository).save(any(Basket.class));
        }
    }

    @Nested
    @DisplayName("addItem")
    class AddItem {

        private final AddBasketItemRequest request = new AddBasketItemRequest(
                "126508", Priority.PREFERRED, "쌍계사", "https://img/1.jpg", "12"
        );

        @Test
        @DisplayName("바구니가 있고 중복이 아니면 항목을 추가한다")
        void noDuplicate_addsItem() {
            // given
            Basket basket = newBasket();
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            BasketItemResponse response = basketService.addItem(USER_ID, request);

            // then
            assertThat(basket.getItems()).hasSize(1);
            assertThat(response.contentId()).isEqualTo("126508");
            assertThat(response.priority()).isEqualTo(Priority.PREFERRED);
            assertThat(response.title()).isEqualTo("쌍계사");
        }

        @Test
        @DisplayName("이미 담은 콘텐츠를 추가하면 BASKET_ITEM_DUPLICATE 예외를 던진다")
        void duplicate_throwsException() {
            // given
            Basket basket = newBasket();
            basket.addItem(itemWithId(UUID.randomUUID(), "126508", Priority.MUST_VISIT));
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            ThrowableAssert.ThrowingCallable action = () -> basketService.addItem(USER_ID, request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BASKET_ITEM_DUPLICATE);
        }
    }

    @Nested
    @DisplayName("changePriority")
    class ChangePriority {

        private final UpdateBasketItemPriorityRequest request =
                new UpdateBasketItemPriorityRequest(Priority.OPTIONAL);

        @Test
        @DisplayName("항목이 있으면 우선순위를 변경한다")
        void itemExists_changesPriority() {
            // given
            UUID itemId = UUID.randomUUID();
            Basket basket = newBasket();
            BasketItem item = itemWithId(itemId, "126508", Priority.MUST_VISIT);
            basket.addItem(item);
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            BasketItemResponse response = basketService.changePriority(USER_ID, itemId, request);

            // then
            assertThat(item.getPriority()).isEqualTo(Priority.OPTIONAL);
            assertThat(response.priority()).isEqualTo(Priority.OPTIONAL);
        }

        @Test
        @DisplayName("바구니에 해당 항목이 없으면 BASKET_ITEM_NOT_FOUND 예외를 던진다")
        void itemMissing_throwsException() {
            // given
            Basket basket = newBasket();
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            ThrowableAssert.ThrowingCallable action =
                    () -> basketService.changePriority(USER_ID, UUID.randomUUID(), request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BASKET_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("바구니가 없으면 BASKET_ITEM_NOT_FOUND 예외를 던진다")
        void noBasket_throwsException() {
            // given
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // when
            ThrowableAssert.ThrowingCallable action =
                    () -> basketService.changePriority(USER_ID, UUID.randomUUID(), request);

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BASKET_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItem {

        @Test
        @DisplayName("항목이 있으면 바구니에서 제거한다")
        void itemExists_removesItem() {
            // given
            UUID itemId = UUID.randomUUID();
            Basket basket = newBasket();
            basket.addItem(itemWithId(itemId, "126508", Priority.MUST_VISIT));
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            basketService.removeItem(USER_ID, itemId);

            // then
            assertThat(basket.getItems()).isEmpty();
        }

        @Test
        @DisplayName("바구니에 해당 항목이 없으면 BASKET_ITEM_NOT_FOUND 예외를 던진다")
        void itemMissing_throwsException() {
            // given
            Basket basket = newBasket();
            given(basketRepository.findByUserId(USER_ID)).willReturn(Optional.of(basket));

            // when
            ThrowableAssert.ThrowingCallable action =
                    () -> basketService.removeItem(USER_ID, UUID.randomUUID());

            // then
            assertThatThrownBy(action)
                    .isInstanceOf(PickTripException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BASKET_ITEM_NOT_FOUND);
        }
    }
}
