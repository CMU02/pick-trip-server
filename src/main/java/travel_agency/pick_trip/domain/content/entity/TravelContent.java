package travel_agency.pick_trip.domain.content.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import travel_agency.pick_trip.domain.region.Region;

/**
 * TourAPI 기반 콘텐츠 원천 데이터(보조 캐시). PK는 TourAPI {@code contentid}를 자연키로 사용한다.
 * {@link ContentDetail}과 1:1, {@link ContentImage}와 1:N 관계.
 */
@Getter
@Entity
@Table(name = "travel_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelContent {

    /** TourAPI contentid */
    @Id
    @Column(name = "source_content_id", nullable = false, updatable = false)
    private String sourceContentId;

    @Column(name = "content_type_id")
    private String contentTypeId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region;

    /** PickTrip 내부 카테고리 (contentTypeId + lclsSystm* 매핑) */
    private String category;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private String address;

    private Double latitude;

    private Double longitude;

    private String tel;

    @Column(columnDefinition = "TEXT")
    private String homepage;

    private String firstImage;

    /** TourAPI 원천 수정일(modifiedtime) */
    private String modifiedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataStatus dataStatus;

    @OneToOne(
            mappedBy = "travelContent",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private ContentDetail detail;

    @OneToMany(
            mappedBy = "travelContent",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ContentImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private TravelContent(
            String sourceContentId,
            String contentTypeId,
            String title,
            Region region,
            String category,
            String summary,
            String address,
            Double latitude,
            Double longitude,
            String tel,
            String homepage,
            String firstImage,
            String modifiedTime,
            DataStatus dataStatus
    ) {
        this.sourceContentId = sourceContentId;
        this.contentTypeId = contentTypeId;
        this.title = title;
        this.region = region;
        this.category = category;
        this.summary = summary;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.tel = tel;
        this.homepage = homepage;
        this.firstImage = firstImage;
        this.modifiedTime = modifiedTime;
        this.dataStatus = dataStatus != null ? dataStatus : DataStatus.ACTIVE;
    }

    /** 1:1 상세를 연결한다 (양방향 동기화). */
    public void assignDetail(ContentDetail detail) {
        this.detail = detail;
        detail.assignTravelContent(this);
    }

    /** 이미지를 추가한다 (양방향 동기화). */
    public void addImage(ContentImage image) {
        image.assignTravelContent(this);
        this.images.add(image);
    }

    /** 동기화 배치가 데이터 상태를 갱신할 때 사용한다. */
    public void changeDataStatus(DataStatus dataStatus) {
        this.dataStatus = dataStatus;
    }

    /** TourAPI 원천 필드를 갱신한다 (수집 upsert). null 인자는 해당 필드를 덮어쓰지 않는다. */
    public void updateSourceData(
            String contentTypeId,
            String title,
            String category,
            String summary,
            String address,
            Double latitude,
            Double longitude,
            String tel,
            String homepage,
            String firstImage,
            String modifiedTime
    ) {
        if (contentTypeId != null) this.contentTypeId = contentTypeId;
        if (title != null) this.title = title;
        if (category != null) this.category = category;
        if (summary != null) this.summary = summary;
        if (address != null) this.address = address;
        if (latitude != null) this.latitude = latitude;
        if (longitude != null) this.longitude = longitude;
        if (tel != null) this.tel = tel;
        if (homepage != null) this.homepage = homepage;
        if (firstImage != null) this.firstImage = firstImage;
        if (modifiedTime != null) this.modifiedTime = modifiedTime;
    }

    /** 기존 이미지를 모두 교체한다 (orphanRemoval 로 이전 이미지는 제거). */
    public void replaceImages(List<ContentImage> newImages) {
        this.images.clear();
        for (ContentImage image : newImages) {
            addImage(image);
        }
    }
}
