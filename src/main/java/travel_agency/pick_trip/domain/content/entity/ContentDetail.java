package travel_agency.pick_trip.domain.content.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@link TravelContent}와 1:1 상세 정보. PK를 {@code @MapsId}로 공유한다
 * ({@code content_details.source_content_id}가 FK 겸 PK).
 * TourAPI {@code /detailIntro2}, {@code /detailInfo2} 기반 + 자체 검수 필드.
 */
@Getter
@Entity
@Table(name = "content_details")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentDetail {

    @Id
    @Column(name = "source_content_id")
    private String sourceContentId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_content_id")
    private TravelContent travelContent;

    private String useTime;

    private String restDate;

    private String parking;

    private String chkBabyCarriage;

    private String chkPet;

    private String useFee;

    // --- 축제 기간 (/searchFestival2, contentTypeId=15) ---

    /** 축제 시작일 (yyyyMMdd) */
    private String eventStartDate;

    /** 축제 종료일 (yyyyMMdd) */
    private String eventEndDate;

    // --- 자체 검수 필드 (이슈 A에서는 컬럼만 정의) ---

    private Boolean reservationRequired;

    @Enumerated(EnumType.STRING)
    private IndoorOutdoor indoorOutdoor;

    @Enumerated(EnumType.STRING)
    private WalkingLevel walkingLevel;

    @ElementCollection
    @CollectionTable(
            name = "content_detail_family_tags",
            joinColumns = @JoinColumn(name = "source_content_id")
    )
    @Column(name = "family_tag")
    private Set<String> familyTags = new HashSet<>();

    private LocalDateTime dataVerifiedAt;

    @Builder
    private ContentDetail(
            String useTime,
            String restDate,
            String parking,
            String chkBabyCarriage,
            String chkPet,
            String useFee
    ) {
        this.useTime = useTime;
        this.restDate = restDate;
        this.parking = parking;
        this.chkBabyCarriage = chkBabyCarriage;
        this.chkPet = chkPet;
        this.useFee = useFee;
    }

    void assignTravelContent(TravelContent travelContent) {
        this.travelContent = travelContent;
    }

    /** {@code /detailIntro2} 기반 상세 필드를 갱신한다 (수집 upsert). */
    public void updateIntro(
            String useTime,
            String restDate,
            String parking,
            String chkBabyCarriage,
            String chkPet,
            String useFee
    ) {
        this.useTime = useTime;
        this.restDate = restDate;
        this.parking = parking;
        this.chkBabyCarriage = chkBabyCarriage;
        this.chkPet = chkPet;
        this.useFee = useFee;
    }

    /** 축제 기간을 갱신한다 ({@code /searchFestival2}). */
    public void updateEventPeriod(String eventStartDate, String eventEndDate) {
        this.eventStartDate = eventStartDate;
        this.eventEndDate = eventEndDate;
    }
}
