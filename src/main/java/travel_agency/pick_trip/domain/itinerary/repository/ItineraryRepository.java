package travel_agency.pick_trip.domain.itinerary.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    /**
     * 일정을 일차·항목과 함께 조회한다. EntityGraph 로 연관 컬렉션을 로딩해 N+1 을 방지한다.
     */
    @EntityGraph(attributePaths = {"days", "days.items"})
    Optional<Itinerary> findWithDaysById(UUID itineraryId);
}
