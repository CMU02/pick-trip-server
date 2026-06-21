package travel_agency.pick_trip.domain.itinerary.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.itinerary.entity.Itinerary;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    /**
     * 일정을 일차·항목과 함께 조회한다. EntityGraph 로 연관 컬렉션을 로딩해 N+1 을 방지한다.
     *
     * <p>PK 필드명이 {@code itineraryId}이므로 파생 쿼리({@code ...ById})로는 {@code id} 속성을 찾지 못한다.
     * 명시적 JPQL 로 PK 조건을 지정한다.
     */
    @EntityGraph(attributePaths = {"days", "days.items"})
    @Query("select i from Itinerary i where i.itineraryId = :itineraryId")
    Optional<Itinerary> findWithDaysById(@Param("itineraryId") UUID itineraryId);
}
