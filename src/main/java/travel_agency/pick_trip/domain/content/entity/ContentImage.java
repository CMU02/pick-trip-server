package travel_agency.pick_trip.domain.content.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link TravelContent}와 1:N 콘텐츠 이미지. TourAPI {@code /detailImage2} 우선,
 * 부족 시 관광사진 갤러리({@code PHOTO_GALLERY}) 보조.
 */
@Getter
@Entity
@Table(name = "content_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "image_id", nullable = false, updatable = false)
    private UUID imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_content_id", nullable = false)
    private TravelContent travelContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageSource source;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    private String title;

    private String copyrightType;

    private String photographyMonth;

    @Builder
    private ContentImage(
            ImageSource source,
            String imageUrl,
            String title,
            String copyrightType,
            String photographyMonth
    ) {
        this.source = source;
        this.imageUrl = imageUrl;
        this.title = title;
        this.copyrightType = copyrightType;
        this.photographyMonth = photographyMonth;
    }

    void assignTravelContent(TravelContent travelContent) {
        this.travelContent = travelContent;
    }
}
