package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class OAuthProviderException extends PickTripException {

    public OAuthProviderException(ErrorCode errorCode) {
        super(errorCode);
    }
}
