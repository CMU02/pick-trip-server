package travel_agency.pick_trip.domain.basket.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.basket.dto.request.AddBasketItemRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketConditionsRequest;
import travel_agency.pick_trip.domain.basket.dto.request.UpdateBasketItemRequest;
import travel_agency.pick_trip.domain.basket.dto.response.BasketItemResponse;
import travel_agency.pick_trip.domain.basket.dto.response.BasketResponse;
import travel_agency.pick_trip.domain.basket.entity.Basket;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.repository.BasketRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.BasketException;

/**
 * 여행 바구니 유스케이스.
 * 사용자당 바구니는 1개이며, 조건 설정·항목 추가 등 첫 쓰기 시점에 바구니를 생성(lazy)한다.
 */
@Service
@RequiredArgsConstructor
public class BasketService {

    private final BasketRepository basketRepository;

    /**
     * 사용자 바구니를 조회한다. 바구니가 없으면 빈 바구니를 반환한다.
     */
    @Transactional(readOnly = true)
    public BasketResponse getBasket(UUID userId) {
        return basketRepository.findByUserId(userId)
                .map(BasketResponse::from)
                .orElseGet(BasketResponse::empty);
    }

    /**
     * 여행 조건을 저장/갱신한다. 바구니가 없으면 생성한다.
     */
    @Transactional
    public BasketResponse updateConditions(UUID userId, UpdateBasketConditionsRequest request) {
        Basket basket = getOrCreateBasket(userId);
        basket.updateConditions(
                request.region(),
                request.travelDate(),
                request.duration(),
                request.companions()
        );
        return BasketResponse.from(basket);
    }

    /**
     * 바구니에 콘텐츠를 추가한다. 바구니가 없으면 생성하며, 같은 콘텐츠가 이미 있으면 예외를 던진다.
     */
    @Transactional
    public BasketItemResponse addItem(UUID userId, AddBasketItemRequest request) {
        Basket basket = getOrCreateBasket(userId);
        if (basket.hasContent(request.contentId())) {
            throw new BasketException(ErrorCode.BASKET_ITEM_DUPLICATE);
        }
        BasketItem item = BasketItem.builder()
                .contentId(request.contentId())
                .title(request.title())
                .thumbnailUrl(request.thumbnailUrl())
                .contentTypeId(request.contentTypeId())
                .priority(request.priority())
                .desiredStayMinutes(request.desiredStayMinutes())
                .build();
        basket.addItem(item);
        return BasketItemResponse.from(item);
    }

    /**
     * 바구니 항목을 부분 갱신한다. {@code null} 인 필드는 변경하지 않는다.
     *
     * <p>ponytail: null 을 "변경 없음"으로 쓰므로 desiredStayMinutes 를 지정한 뒤
     * 카테고리 기본값으로 되돌릴 방법이 없다({@code UpdateBasketConditionsRequest} 와 동일한 한계).
     * 비우기가 필요해지면 JsonNullable 같은 래퍼로 필드 부재와 명시적 null 을 구분한다.
     */
    @Transactional
    public BasketItemResponse updateItem(UUID userId, UUID itemId, UpdateBasketItemRequest request) {
        BasketItem item = findOwnedItem(userId, itemId);
        if (request.priority() != null) {
            item.changePriority(request.priority());
        }
        if (request.desiredStayMinutes() != null) {
            item.changeDesiredStayMinutes(request.desiredStayMinutes());
        }
        return BasketItemResponse.from(item);
    }

    /**
     * 바구니에서 콘텐츠를 제거한다.
     */
    @Transactional
    public void removeItem(UUID userId, UUID itemId) {
        Basket basket = findOwnedBasket(userId);
        BasketItem item = basket.findItem(itemId)
                .orElseThrow(() -> new BasketException(ErrorCode.BASKET_ITEM_NOT_FOUND));
        basket.removeItem(item);
    }

    private Basket getOrCreateBasket(UUID userId) {
        return basketRepository.findByUserId(userId)
                .orElseGet(() -> basketRepository.save(Basket.builder().userId(userId).build()));
    }

    private Basket findOwnedBasket(UUID userId) {
        return basketRepository.findByUserId(userId)
                .orElseThrow(() -> new BasketException(ErrorCode.BASKET_ITEM_NOT_FOUND));
    }

    /**
     * 사용자 소유 바구니에서 항목을 찾는다. 바구니/항목이 없으면 동일하게 NOT_FOUND로 처리해
     * 다른 사용자의 항목 존재 여부가 노출되지 않도록 한다.
     */
    private BasketItem findOwnedItem(UUID userId, UUID itemId) {
        return findOwnedBasket(userId).findItem(itemId)
                .orElseThrow(() -> new BasketException(ErrorCode.BASKET_ITEM_NOT_FOUND));
    }
}
