package travel_agency.pick_trip.domain.region.entity;

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
 * TourAPI {@code /areaCode2} 지역 코드. 시도는 {@code parentCode = ""}, 시군구는 시도 코드를 {@code parentCode}로 갖는다.
 * 시군구 코드는 시도 내에서만 유일하므로 {@code (code, parentCode)} 복합 자연키에 unique 제약을 둔다.
 */
@Getter
@Entity
@Table(
        name = "area_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_area_codes_code_parent", columnNames = {"code", "parent_code"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AreaCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "area_code_id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** 최상위(시도)는 빈 문자열, 시군구는 상위 시도 코드 */
    @Column(name = "parent_code", nullable = false)
    private String parentCode;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private AreaCode(String code, String name, String parentCode) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode != null ? parentCode : "";
    }

    /** upsert 시 변경 필드 갱신. */
    public void update(String name) {
        this.name = name;
    }
}
