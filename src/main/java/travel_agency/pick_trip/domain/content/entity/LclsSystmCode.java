package travel_agency.pick_trip.domain.content.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * TourAPI {@code /lclsSystmCode2} 신분류체계 코드(1/2/3 Depth). 최상위는 {@code parentCode = ""}.
 * {@code (code, parentCode)} 복합 자연키에 unique 제약을 둔다.
 */
@Getter
@Entity
@Table(
        name = "lcls_systm_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_lcls_systm_codes_code_parent", columnNames = {"code", "parent_code"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LclsSystmCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "lcls_systm_code_id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** 최상위(1Depth)는 빈 문자열, 하위는 상위 코드 */
    @Column(name = "parent_code", nullable = false)
    private String parentCode;

    /** 분류 깊이 (1/2/3 Depth) */
    @Column(nullable = false)
    private int depth;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private LclsSystmCode(String code, String name, String parentCode, int depth) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode != null ? parentCode : "";
        this.depth = depth;
    }

    /** upsert 시 변경 필드 갱신. */
    public void update(String name) {
        this.name = name;
    }
}
