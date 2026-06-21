package travel_agency.pick_trip.domain.content.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import travel_agency.pick_trip.domain.content.entity.ContentImage;
import travel_agency.pick_trip.domain.content.entity.ImageSource;

public interface ContentImageRepository extends JpaRepository<ContentImage, UUID> {

    List<ContentImage> findByTravelContent_SourceContentId(String sourceContentId);

    List<ContentImage> findBySourceAndImageUrl(ImageSource source, String imageUrl);
}
