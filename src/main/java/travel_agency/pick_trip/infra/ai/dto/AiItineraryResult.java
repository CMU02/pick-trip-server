package travel_agency.pick_trip.infra.ai.dto;

import java.util.List;

/**
 * AI 일정 생성 응답 모델 (structured output 으로 파싱되는 스키마).
 * 일정 제목과 일차별 장소 배치(콘텐츠 ID·순서·배치 이유)를 담는다.
 * 장소의 표시 정보(title 등)는 AI 응답을 신뢰하지 않고 바구니 스냅샷에서 다시 매핑한다.
 */
public record AiItineraryResult(
        String title,
        List<AiDay> days
) {

    public record AiDay(
            int dayIndex,
            List<AiItem> items
    ) {
    }

    public record AiItem(
            String contentId,
            int order,
            String reason
    ) {
    }
}
