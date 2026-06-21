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
 * TourAPI {@code /ldongCode2} 법정동 코드. 시도 레벨은 {@code signguCd = ""},
 * 시군구 레벨은 시군구 코드를 갖는다. {@code (regnCd, signguCd)} 복합 자연키에 unique 제약을 둔다.
 */
@Getter
@Entity
@Table(
        name = "ldong_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_ldong_codes_regn_signgu", columnNames = {"regn_cd", "signgu_cd"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LdongCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ldong_code_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "regn_cd", nullable = false)
    private String regnCd;

    /** 시도 레벨은 빈 문자열, 시군구 레벨은 시군구 코드 */
    @Column(name = "signgu_cd", nullable = false)
    private String signguCd;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private LdongCode(String regnCd, String signguCd, String name) {
        this.regnCd = regnCd;
        this.signguCd = signguCd != null ? signguCd : "";
        this.name = name;
    }

    /** upsert 시 변경 필드 갱신. */
    public void update(String name) {
        this.name = name;
    }
}
