package travel_agency.pick_trip.domain.content.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.content.entity.DataStatus;
import travel_agency.pick_trip.domain.content.entity.TravelContent;
import travel_agency.pick_trip.domain.region.Region;

public interface TravelContentRepository extends JpaRepository<TravelContent, String> {

    List<TravelContent> findByRegion(Region region);

    List<TravelContent> findByRegionAndDataStatus(Region region, DataStatus dataStatus);
}
