package travel_agency.pick_trip.domain.region.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.region.entity.AreaCode;

public interface AreaCodeRepository extends JpaRepository<AreaCode, UUID> {

    Optional<AreaCode> findByCodeAndParentCode(String code, String parentCode);
}
