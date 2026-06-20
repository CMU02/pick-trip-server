package travel_agency.pick_trip.domain.basket.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import travel_agency.pick_trip.domain.basket.entity.Basket;

@Repository
public interface BasketRepository extends JpaRepository<Basket, UUID> {

    /**
     * 사용자 바구니를 항목·동행 조건과 함께 조회한다.
     * EntityGraph로 연관 컬렉션을 함께 로딩해 N+1을 방지한다.
     */
    @EntityGraph(attributePaths = {"items", "companions"})
    Optional<Basket> findByUserId(UUID userId);
}
