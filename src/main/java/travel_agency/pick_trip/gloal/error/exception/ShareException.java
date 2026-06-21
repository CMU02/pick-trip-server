package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

/**
 * 공유(share) 도메인 예외. 공유 토큰 미존재·권한 없음·생성 실패 등을 표현한다.
 */
public class ShareException extends PickTripException {
    public ShareException(ErrorCode errorCode) {
        super(errorCode);
    }
}
