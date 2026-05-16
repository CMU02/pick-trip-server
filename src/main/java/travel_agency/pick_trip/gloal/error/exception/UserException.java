package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class UserException extends PickTripException {

    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
