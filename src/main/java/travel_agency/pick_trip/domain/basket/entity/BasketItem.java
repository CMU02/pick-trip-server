package travel_agency.pick_trip.domain.basket.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 바구니에 담은 개별 콘텐츠.
 * content는 DB에 영속화하지 않으므로 TourAPI contentId(문자열)를 그대로 저장하고,
 * 바구니 조회 시 TourAPI 재조회 없이 표시할 수 있도록 표시용 스냅샷(title, thumbnailUrl, contentTypeId)을 함께 보관한다.
 */
@Getter
@Entity
@Table(
        name = "basket_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_basket_items_basket_content",
                        columnNames = {"basket_id", "content_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasketItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basket_id", nullable = false)
    private Basket basket;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(length = 200)
    private String title;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "content_type_id", length = 20)
    private String contentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private BasketItem(
            String contentId,
            String title,
            String thumbnailUrl,
            String contentTypeId,
            Priority priority
    ) {
        this.contentId = contentId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.contentTypeId = contentTypeId;
        this.priority = priority;
    }

    /**
     * 양방향 연관관계 설정 (Basket.addItem에서만 호출).
     */
    void assignBasket(Basket basket) {
        this.basket = basket;
    }

    public void changePriority(Priority priority) {
        this.priority = priority;
    }
}
