package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class FavoriteException extends PickTripException {
    public FavoriteException(ErrorCode errorCode) {
        super(errorCode);
    }
}
