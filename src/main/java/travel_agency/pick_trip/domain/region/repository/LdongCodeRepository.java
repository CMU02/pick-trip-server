package travel_agency.pick_trip.domain.region.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.region.entity.LdongCode;

public interface LdongCodeRepository extends JpaRepository<LdongCode, UUID> {

    Optional<LdongCode> findByRegnCdAndSignguCd(String regnCd, String signguCd);
}
