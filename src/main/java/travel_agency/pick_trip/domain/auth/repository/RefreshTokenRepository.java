package travel_agency.pick_trip.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.auth.entity.RefreshToken;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);
}
