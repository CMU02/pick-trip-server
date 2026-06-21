package travel_agency.pick_trip.domain.content.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.content.entity.CategoryCode;

public interface CategoryCodeRepository extends JpaRepository<CategoryCode, UUID> {

    Optional<CategoryCode> findByCodeAndParentCode(String code, String parentCode);
}
