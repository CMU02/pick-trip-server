package travel_agency.pick_trip.domain.content.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.content.entity.LclsSystmCode;

public interface LclsSystmCodeRepository extends JpaRepository<LclsSystmCode, UUID> {

    Optional<LclsSystmCode> findByCodeAndParentCode(String code, String parentCode);
}
