package travel_agency.pick_trip.domain.favorite.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import travel_agency.pick_trip.domain.content.entity.ContentCategory;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 사용자가 찜한 콘텐츠.
 * content는 DB에 영속화하지 않으므로 TourAPI contentId(문자열)를 그대로 저장하고,
 * 찜 목록 조회 시 TourAPI 재조회 없이 표시할 수 있도록 표시용 스냅샷을 함께 보관한다
 * (basket_items와 동일 정책).
 */
@Getter
@Entity
@Table(
        name = "favorites",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_favorites_user_content",
                        columnNames = {"user_id", "content_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String address;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContentCategory category;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Boolean indoor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Favorite(
            UUID userId,
            String contentId,
            String title,
            String address,
            String firstImage,
            ContentCategory category,
            String summary,
            Boolean indoor,
            Region region
    ) {
        this.userId = userId;
        this.contentId = contentId;
        this.title = title;
        this.address = address;
        this.firstImage = firstImage;
        this.category = category;
        this.summary = summary;
        this.indoor = indoor;
        this.region = region;
    }
}
