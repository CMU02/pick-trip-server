package travel_agency.pick_trip.domain.favorite.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.favorite.entity.Favorite;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Favorite> findByUserIdAndContentId(UUID userId, String contentId);

    boolean existsByUserIdAndContentId(UUID userId, String contentId);
}
