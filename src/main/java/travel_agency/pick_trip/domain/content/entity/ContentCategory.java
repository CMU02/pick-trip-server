package travel_agency.pick_trip.domain.content.entity;

import java.util.Map;

/**
 * PickTrip 내부 콘텐츠 분류. TourAPI 신분류체계({@code lclsSystm1}/{@code lclsSystm2}) 원본 코드를
 * 6종으로 그룹핑한다. 코드 테이블(seed) 상태와 무관하게 정적 매핑만으로 판정한다.
 */
public enum ContentCategory {
    FOOD,
    FESTIVAL,
    ATTRACTION,
    CULTURE,
    NATURE,
    EXPERIENCE;

    /** depth2(중분류) 코드가 대분류 기본값과 다르게 세분화되어야 하는 경우의 예외 매핑. */
    private static final Map<String, ContentCategory> DEPTH2_OVERRIDES = Map.ofEntries(
            Map.entry("C0117", FOOD),          // 맛코스

            // VE(문화관광)는 대분류 하나로 묶기엔 성격이 갈려 depth2 단위로 개별 판단한다.
            Map.entry("VE01", ATTRACTION),     // 랜드마크관광
            Map.entry("VE02", ATTRACTION),     // 테마공원
            Map.entry("VE03", NATURE),         // 도시공원
            Map.entry("VE04", CULTURE),        // 도시·지역문화관광
            Map.entry("VE05", ATTRACTION),     // 복합관광시설
            Map.entry("VE06", CULTURE),        // 공연시설
            Map.entry("VE07", CULTURE),        // 전시시설
            Map.entry("VE08", ATTRACTION),     // 행사시설
            Map.entry("VE09", CULTURE),        // 교육시설
            Map.entry("VE10", EXPERIENCE),     // 레저스포츠시설
            Map.entry("VE11", ATTRACTION),     // 교통시설
            Map.entry("VE12", CULTURE)         // 기타문화관광지
    );

    /** depth1(대분류) 코드 기본 매핑. {@code AC}(숙박)는 MVP 수집 대상이 아니라 제외한다. */
    private static final Map<String, ContentCategory> DEPTH1_DEFAULTS = Map.of(
            "FD", FOOD,         // 음식
            "EV", FESTIVAL,     // 축제/공연/행사
            "NA", NATURE,       // 자연관광
            "HS", CULTURE,      // 역사관광
            "EX", EXPERIENCE,   // 체험관광
            "LS", EXPERIENCE,   // 레저스포츠
            "SH", ATTRACTION,   // 쇼핑
            "C01", ATTRACTION,  // 추천코스
            "VE", ATTRACTION    // 문화관광 (depth2 override 없을 때 기본값)
    );

    /**
     * TourAPI 신분류체계 원본 코드를 내부 카테고리로 변환한다. depth2 예외 매핑을 우선 적용하고,
     * 없으면 depth1 기본 매핑을 적용한다. 둘 다 없으면 {@link #ATTRACTION}으로 대체한다.
     */
    public static ContentCategory from(String lclsSystm1, String lclsSystm2) {
        if (lclsSystm2 != null && DEPTH2_OVERRIDES.containsKey(lclsSystm2)) {
            return DEPTH2_OVERRIDES.get(lclsSystm2);
        }
        if (lclsSystm1 != null && DEPTH1_DEFAULTS.containsKey(lclsSystm1)) {
            return DEPTH1_DEFAULTS.get(lclsSystm1);
        }
        return ATTRACTION;
    }
}
