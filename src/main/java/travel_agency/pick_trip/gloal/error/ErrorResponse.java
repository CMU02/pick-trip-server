package travel_agency.pick_trip.gloal.error;

public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
    public static ErrorResponse of(ErrorCode errorCode, String traceId) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), traceId);
    }
}
