package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class BasketException extends PickTripException {
    public BasketException(ErrorCode errorCode) {
        super(errorCode);
    }
}
