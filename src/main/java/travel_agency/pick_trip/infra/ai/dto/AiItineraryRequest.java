package travel_agency.pick_trip.infra.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import travel_agency.pick_trip.domain.basket.entity.TravelCondition;

/**
 * AI 일정 생성 요청 입력 모델.
 * 여행 조건(지역·날짜·기간·동행)과 장소 목록을 담아 {@code AiItineraryClient}에 전달한다.
 */
public record AiItineraryRequest(
        String regionName,
        LocalDate travelDate,
        Integer duration,
        Set<TravelCondition> companions,
        List<AiPlace> places
) {
}
