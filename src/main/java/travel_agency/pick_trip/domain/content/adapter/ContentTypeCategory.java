package travel_agency.pick_trip.domain.content.adapter;

import travel_agency.pick_trip.domain.content.dto.request.CompanionType;

public enum ContentTypeCategory {
    TOURISM("12", "약 2시간"),
    CULTURE("14", "약 1~2시간"),
    EVENT("15", "약 2~3시간"),
    LEISURE("28", "약 2~3시간"),
    SHOPPING("38", "약 1시간"),
    RESTAURANT("39", "약 1시간");

    private final String contentTypeId;
    private final String stayDuration;

    ContentTypeCategory(String contentTypeId, String stayDuration) {
        this.contentTypeId = contentTypeId;
        this.stayDuration = stayDuration;
    }

    public static String resolveContentTypeId(String explicit, Boolean indoorOnly, CompanionType companion) {
        if (explicit != null) return explicit;
        if (indoorOnly != null) {
            return indoorOnly ? CULTURE.contentTypeId : TOURISM.contentTypeId;
        }
        if (companion == CompanionType.FAMILY) {
            return CULTURE.contentTypeId;
        }
        return null;
    }

    public static String stayDurationFor(int contentTypeId) {
        String id = String.valueOf(contentTypeId);
        for (ContentTypeCategory c : values()) {
            if (c.contentTypeId.equals(id)) {
                return c.stayDuration;
            }
        }
        return null;
    }
}
