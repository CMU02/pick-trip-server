package travel_agency.pick_trip.domain.basket.dto.response;

import java.util.UUID;
import travel_agency.pick_trip.domain.basket.entity.BasketItem;
import travel_agency.pick_trip.domain.basket.entity.Priority;

/**
 * 바구니 항목 응답.
 */
public record BasketItemResponse(
        UUID itemId,
        String contentId,
        String title,
        String thumbnailUrl,
        String contentTypeId,
        Priority priority
) {

    public static BasketItemResponse from(BasketItem item) {
        return new BasketItemResponse(
                item.getItemId(),
                item.getContentId(),
                item.getTitle(),
                item.getThumbnailUrl(),
                item.getContentTypeId(),
                item.getPriority()
        );
    }
}
