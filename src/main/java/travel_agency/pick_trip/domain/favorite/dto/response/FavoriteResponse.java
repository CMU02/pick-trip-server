package travel_agency.pick_trip.domain.favorite.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 찜 항목 응답. 필드명은 /api/v1/contents 응답 규약(contentId/title/address/firstImage/...)을 따라
 * 프론트가 기존 매퍼를 재사용할 수 있게 한다.
 */
public record FavoriteResponse(
        UUID id,
        String contentId,
        String title,
        String address,
        String firstImage,
        ContentCategory category,
        String summary,
        Boolean indoor,
        Region region,
        LocalDateTime createdAt
) {

    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getContentId(),
                favorite.getTitle(),
                favorite.getAddress(),
                favorite.getFirstImage(),
                favorite.getCategory(),
                favorite.getSummary(),
                favorite.getIndoor(),
                favorite.getRegion(),
                favorite.getCreatedAt()
        );
    }
}
