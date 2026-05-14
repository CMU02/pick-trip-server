package travel_agency.pick_trip.gloal.error.exception;

import lombok.Getter;
import travel_agency.pick_trip.gloal.error.ErrorCode;

@Getter
public class PickTripException extends RuntimeException {

    private final ErrorCode errorCode;

    public PickTripException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
