package travel_agency.pick_trip.gloal.error.exception;

import travel_agency.pick_trip.gloal.error.ErrorCode;

public class ContentException extends PickTripException {
  public ContentException(ErrorCode errorCode) {
    super(errorCode);
  }
}
