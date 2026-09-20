package travel_agency.pick_trip.infra.ai.dto;

/**
 * AI 일정 생성에 전달하는 개별 장소 입력.
 * 바구니 스냅샷(contentId·title·priority)에 더해, 가능하면 콘텐츠 상세
 * (좌표·운영시간·휴무일·체류시간)를 보강해 제약 기반 동선 추론에 활용한다.
 * 상세 조회 실패 시 좌표·운영시간 등은 null 일 수 있다.
 *
 * <p>{@code priority} 는 enum 코드가 아니라 한국어 라벨이다 ({@code Priority.getLabel()}).
 *
 * <p>{@code desiredStayMinutes} 는 바구니 항목에 사용자가 직접 지정한 희망 체류시간(분)이다.
 * 있으면 {@code stayDuration}(카테고리 기본값)보다 우선해서 프롬프트에 실린다.
 */
public record AiPlace(
        String contentId,
        String title,
        String category,
        Double latitude,
        Double longitude,
        String useTime,
        String restDate,
        String stayDuration,
        String priority,
        Integer desiredStayMinutes
) {
    /** AUGMENT 후보처럼 id·이름·분류만 아는 장소를 만든다. 상세·우선순위·지정 체류시간은 없다. */
    public static AiPlace candidate(String contentId, String title, String category) {
        return new AiPlace(contentId, title, category, null, null, null, null, null, null, null);
    }
}
