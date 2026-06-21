package travel_agency.pick_trip.domain.content.entity;

/**
 * 실내/실외 구분. 자체 검수 + 분류체계로 채운다 (이슈 A에서는 컬럼만 정의).
 */
public enum IndoorOutdoor {
    INDOOR,
    OUTDOOR,
    MIXED
}
