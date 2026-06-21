package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

/**
 * 일정(itinerary) 도메인 예외.
 * AI 일정 생성 입력 부족, 외부 AI 호출 실패·타임아웃, 저장 실패 등을 표현한다.
 */
public class ItineraryException extends PickTripException {
    public ItineraryException(ErrorCode errorCode) {
        super(errorCode);
    }
}
