package travel_agency.pick_trip.infra.ai;

import travel_agency.pick_trip.infra.ai.dto.AiItineraryRequest;
import travel_agency.pick_trip.infra.ai.dto.AiItineraryResult;

/**
 * AI 일정 생성 제공자 호출 인터페이스.
 * 도메인 코드는 이 인터페이스에만 의존하며, 프로바이더별 구현체는 {@code infra.ai} 패키지에 둔다.
 * 구현체는 외부 호출 실패·타임아웃·파싱 실패를 {@code ItineraryException}
 * (ITINERARY_PROVIDER_FAILED / ITINERARY_GENERATION_TIMEOUT) 으로 변환해 던진다.
 */
public interface AiItineraryClient {

    AiItineraryResult generate(AiItineraryRequest request);
}
