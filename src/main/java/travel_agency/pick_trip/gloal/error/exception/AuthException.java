package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class AuthException extends PickTripException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
