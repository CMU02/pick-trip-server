package travel_agency.pick_trip.domain.itinerary.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import travel_agency.pick_trip.domain.region.Region;

/**
 * 저장된 여행 일정 (사용자 소유). 일정 → 일차 → 항목 계층의 최상위.
 * AI 생성 미리보기를 사용자가 저장하면 영속화되며, 수정·공유의 대상이 된다.
 */
@Getter
@Entity
@Table(name = "itineraries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID itineraryId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Region region;

    @Column(name = "travel_date")
    private LocalDate travelDate;

    @Column
    private Integer duration;

    @OneToMany(
            mappedBy = "itinerary",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("dayIndex ASC")
    private List<ItineraryDay> days = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;

    @Builder
    private Itinerary(UUID userId, String title, Region region, LocalDate travelDate, Integer duration) {
        this.userId = userId;
        this.title = title;
        this.region = region;
        this.travelDate = travelDate;
        this.duration = duration;
    }

    public void addDay(ItineraryDay day) {
        day.assignItinerary(this);
        this.days.add(day);
    }

    /**
     * 일정의 일차·항목 구성을 통째로 교체한다 (순서 변경·삭제·추가·고정 수정을 한 번에 반영).
     * 기존 일차는 orphanRemoval 로 삭제된다.
     */
    public void replaceDays(List<ItineraryDay> newDays) {
        this.days.clear();
        for (ItineraryDay day : newDays) {
            addDay(day);
        }
    }

    public void updateTitle(String title) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }
}
