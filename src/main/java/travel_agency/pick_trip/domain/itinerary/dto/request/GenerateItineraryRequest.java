package travel_agency.pick_trip.domain.itinerary.dto.request;

/**
 * AI 일정 생성 요청 옵션. 바디 없이 호출하는 기존 클라이언트를 깨지 않기 위해 모든 필드는 선택값이며,
 * 누락 시 기존 동작({@link GenerateMode#STRICT}, 시작 지점 없음)과 동일하게 정규화한다.
 *
 * @param startContentId 여행을 시작할 바구니 항목의 contentId. 일차 배분과 하루 동선 최적화는
 *                       지정 여부와 무관하게 수행하며, 지정하면 이 장소를 첫 스톱으로 고정한다.
 *                       미지정이면 최적화 결과의 첫 장소에서 시작한다.
 */
public record GenerateItineraryRequest(
        GenerateMode mode,
        String startContentId
) {

    public GenerateItineraryRequest {
        // 서비스가 매번 null 을 방어하지 않도록 진입 시점에 한 번만 정규화한다.
        mode = mode == null ? GenerateMode.STRICT : mode;
        // 빈 문자열은 "미지정"과 같은 의미다. 여기서 null 로 모아두면 뒤에서 isBlank 검사를 반복하지 않는다.
        startContentId = (startContentId == null || startContentId.isBlank()) ? null : startContentId.trim();
    }

    /** 시작 지점 없이 모드만 지정하는 호출. 필드가 늘어나도 기존 호출부가 그대로 남도록 둔다. */
    public GenerateItineraryRequest(GenerateMode mode) {
        this(mode, null);
    }

    /** 바디 없이 호출된 경우 사용할 기본 요청. 필드가 늘어나도 호출부가 그대로 남도록 팩터리로 둔다. */
    public static GenerateItineraryRequest defaults() {
        return new GenerateItineraryRequest(null, null);
    }
}
