package travel_agency.pick_trip.domain.share.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 일정 공유 링크 토큰. 예측 불가능한 토큰으로 공개 일정을 조회할 수 있게 한다.
 * 비활성화(active=false) 시 더 이상 조회되지 않는다.
 */
@Getter
@Entity
@Table(
        name = "share_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_share_tokens_token", columnNames = {"token"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID shareTokenId;

    @Column(name = "itinerary_id", nullable = false, updatable = false)
    private UUID itineraryId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ShareToken(UUID itineraryId, String token) {
        this.itineraryId = itineraryId;
        this.token = token;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
