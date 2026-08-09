package travel_agency.pick_trip.domain.favorite.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import travel_agency.pick_trip.domain.favorite.dto.request.AddFavoriteRequest;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoriteResponse;
import travel_agency.pick_trip.domain.favorite.dto.response.FavoritesResponse;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;
import travel_agency.pick_trip.domain.favorite.repository.FavoriteRepository;
import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.FavoriteException;

/**
 * 찜하기 유스케이스.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    @Transactional(readOnly = true)
    public FavoritesResponse getFavorites(UUID userId) {
        List<FavoriteResponse> items = favoriteRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FavoriteResponse::from)
                .toList();
        return new FavoritesResponse(items);
    }

    @Transactional
    public FavoriteResponse addFavorite(UUID userId, AddFavoriteRequest request) {
        if (favoriteRepository.existsByUserIdAndContentId(userId, request.contentId())) {
            throw new FavoriteException(ErrorCode.FAVORITE_DUPLICATE);
        }
        Favorite favorite = Favorite.builder()
                .userId(userId)
                .contentId(request.contentId())
                .title(request.title())
                .address(request.address())
                .firstImage(request.firstImage())
                .category(request.category())
                .summary(request.summary())
                .indoor(request.indoor())
                .region(request.region())
                .build();
        try {
            return FavoriteResponse.from(favoriteRepository.save(favorite));
        } catch (DataIntegrityViolationException e) {
            throw new FavoriteException(ErrorCode.FAVORITE_DUPLICATE);
        }
    }

    @Transactional
    public void removeFavorite(UUID userId, String contentId) {
        Favorite favorite = favoriteRepository.findByUserIdAndContentId(userId, contentId)
                .orElseThrow(() -> new FavoriteException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
    }
}
