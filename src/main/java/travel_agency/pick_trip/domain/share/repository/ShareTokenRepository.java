package travel_agency.pick_trip.domain.share.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.share.entity.ShareToken;

@Repository
public interface ShareTokenRepository extends JpaRepository<ShareToken, UUID> {

    Optional<ShareToken> findByTokenAndActiveTrue(String token);

    Optional<ShareToken> findByItineraryIdAndActiveTrue(UUID itineraryId);
}
