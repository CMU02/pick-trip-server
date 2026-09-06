package travel_agency.pick_trip.domain.itinerary.dto.request;

/**
 * AI 일정 생성 요청 옵션. 바디 없이 호출하는 기존 클라이언트를 깨지 않기 위해 모든 필드는 선택값이며,
 * 누락 시 기존 동작({@link GenerateMode#STRICT})과 동일하게 정규화한다.
 */
public record GenerateItineraryRequest(
        GenerateMode mode
) {

    public GenerateItineraryRequest {
        // 서비스가 매번 null 을 방어하지 않도록 진입 시점에 한 번만 정규화한다.
        mode = mode == null ? GenerateMode.STRICT : mode;
    }

    /** 바디 없이 호출된 경우 사용할 기본 요청. 필드가 늘어나도 호출부가 그대로 남도록 팩터리로 둔다. */
    public static GenerateItineraryRequest defaults() {
        return new GenerateItineraryRequest(null);
    }
}
